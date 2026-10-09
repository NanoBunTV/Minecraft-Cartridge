package com.nanobuntv.cartridge.emu;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Properties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Puts the native files the mod needs into {@code <gameDir>/cartridge_mod/}:
 * <ul>
 *   <li>cartridge-host, in {@code natives/<os>-<arch>/} (D218). The mod jar carries no executables (CurseForge refuses
 *       them), only {@code natives/host.properties}: each platform's SHA-256 and the addresses of the public
 *       repository's {@code natives/} folder. The host is downloaded from there once and must match that hash, so
 *       nothing but the file this version was built with ever runs. A player without internet access can put the
 *       file in place by hand. A dev run or a test has the binaries on its classpath and extracts them instead.</li>
 *   <li>the emulator cores (D114, D137), from {@code cores/<os>-<arch>/} to {@code cores/<os>-<arch>/}: each pinned
 *       at its {@link CoreSpec#version()}, the same upstream commit on every platform. Their license texts and source
 *       links are in the jar's {@code cores/<name>/} (the repository's cores/). An add-on's core comes from the
 *       add-on's own jar ({@link CoreSpec#jar()}, D195).</li>
 * </ul>
 * Extracted files are rewritten only when the jar's copy differs (a mod update), and streamed, never held whole: an
 * arcade core is hundreds of megabytes.
 */
public final class Natives {
    private Natives() {}

    private static final Logger LOGGER = LoggerFactory.getLogger("Cartridge");
    /** Far more than cartridge-host will ever be: a guard against a wrong address streaming something huge. */
    private static final long MAX_HOST_BYTES = 32L << 20;

    public static synchronized Path extractHost(Path gameDir, HostPlatform platform) throws IOException {
        String resource = "/natives/" + platform.id + "/" + platform.hostFileName;
        Path target = gameDir.resolve("cartridge_mod").resolve("natives").resolve(platform.id).resolve(platform.hostFileName);
        if (Natives.class.getResource(resource) != null) {
            extract(Natives.class, resource, target); // a dev run or a test
        } else {
            Properties info = new Properties();
            try (InputStream in = open(Natives.class, "/natives/host.properties")) {
                info.load(in);
            }
            String sha256 = info.getProperty("sha256." + platform.id);
            if (sha256 == null) throw new IOException("the mod jar names no cartridge-host for " + platform.id);
            if (!Files.exists(target) || !sha256.equalsIgnoreCase(sha256(target))) {
                List<URI> urls = new ArrayList<>();
                for (int i = 0; info.getProperty("url." + i) != null; i++) {
                    urls.add(URI.create(info.getProperty("url." + i).replace("{platform}", platform.id)
                            .replace("{file}", platform.hostFileName)));
                }
                download(urls, sha256, target);
            }
        }
        if (platform == HostPlatform.LINUX_X86_64) {
            // Files built on Windows carry no exec bit (TechnicalArchitecture, The sidecar), nor do downloads.
            Files.setPosixFilePermissions(target, PosixFilePermissions.fromString("rwxr-xr-x"));
        }
        return target;
    }

    public static Path extractCore(Path gameDir, HostPlatform platform, CoreSpec core) throws IOException {
        String fileName = core.fileName(platform);
        Path target = gameDir.resolve("cartridge_mod").resolve("cores").resolve(platform.id).resolve(fileName);
        Files.deleteIfExists(target.resolveSibling(fileName + ".sha256")); // left by the old core downloader
        return extract(core.jar(), "/cores/" + platform.id + "/" + fileName, target);
    }

    /**
     * Downloads the file with the SHA-256 {@code sha256} to {@code target} from the first of {@code urls} that has it
     * (D218): the release's tag can be missing, or main can hold a newer build.
     */
    static void download(List<URI> urls, String sha256, Path target) throws IOException {
        Path dir = target.getParent();
        Files.createDirectories(dir);
        deleteOldCopies(target);
        String failure = "no address to download it from";
        for (URI url : urls) {
            Path temp = Files.createTempFile(dir, target.getFileName().toString(), ".tmp");
            HttpURLConnection http = null;
            try {
                // java.net (java.base) rather than java.net.http: there on every loader's module setup.
                http = (HttpURLConnection) url.toURL().openConnection();
                http.setConnectTimeout(15_000);
                http.setReadTimeout(30_000);
                http.setRequestProperty("User-Agent", "Cartridge (Minecraft mod)");
                if (http.getResponseCode() != 200) {
                    failure = "HTTP " + http.getResponseCode() + " from " + url;
                    continue;
                }
                String got;
                try (InputStream in = http.getInputStream()) {
                    got = copyHashed(in, temp);
                }
                if (!got.equalsIgnoreCase(sha256)) {
                    failure = url + " isn't the file this version of Cartridge needs";
                    continue;
                }
                install(temp, target);
                LOGGER.info("Downloaded cartridge-host from {}", url);
                return;
            } catch (IOException | IllegalArgumentException e) {
                failure = e.getMessage() == null ? e.toString() : e.getMessage();
            } finally {
                if (http != null) http.disconnect();
                Files.deleteIfExists(temp);
            }
        }
        LOGGER.warn("Couldn't download cartridge-host ({}). Without internet access, download it from {} and put it at"
                + " {} (SHA-256 {})", failure, urls.isEmpty() ? "?" : urls.get(0), target, sha256);
        throw new IOException("Couldn't download cartridge-host: " + failure);
    }

    /** Copies {@code in} to {@code file} and returns its SHA-256 in hex. */
    private static String copyHashed(InputStream in, Path file) throws IOException {
        MessageDigest digest = sha256Digest();
        long total = 0;
        try (OutputStream out = Files.newOutputStream(file)) {
            byte[] buffer = new byte[1 << 16];
            for (int n; (n = in.read(buffer)) > 0; ) {
                total += n;
                if (total > MAX_HOST_BYTES) throw new IOException("the download is far too big for cartridge-host");
                digest.update(buffer, 0, n);
                out.write(buffer, 0, n);
            }
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    static String sha256(Path file) throws IOException {
        MessageDigest digest = sha256Digest();
        try (InputStream in = Files.newInputStream(file)) {
            byte[] buffer = new byte[1 << 16];
            for (int n; (n = in.read(buffer)) > 0; ) digest.update(buffer, 0, n);
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    private static MessageDigest sha256Digest() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e); // every JVM has it
        }
    }

    private static Path extract(Class<?> jar, String resource, Path target) throws IOException {
        if (Files.exists(target) && same(jar, resource, target)) return target;
        Path dir = target.getParent();
        Files.createDirectories(dir);
        deleteOldCopies(target);
        // Write beside it and move, so a running host or a crash mid-write never leaves a half file.
        Path temp = Files.createTempFile(dir, target.getFileName().toString(), ".tmp");
        try (InputStream in = open(jar, resource)) {
            Files.copy(in, temp, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            Files.deleteIfExists(temp);
            throw e;
        }
        install(temp, target);
        return target;
    }

    /** Moves a finished file from beside {@code target} over it. */
    private static void install(Path temp, Path target) throws IOException {
        try {
            Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            // Windows won't overwrite a file another game instance still has loaded, but lets it be renamed.
            if (!Files.exists(target)) throw e;
            Files.move(target, target.resolveSibling(target.getFileName() + ".old-" + System.nanoTime()));
            Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE);
        }
    }

    private static InputStream open(Class<?> jar, String resource) throws IOException {
        InputStream in = jar.getResourceAsStream(resource);
        if (in == null) throw new IOException("the mod jar has no " + resource);
        return in;
    }

    /** Whether the file on disk is the jar's, compared a block at a time. */
    private static boolean same(Class<?> jar, String resource, Path target) throws IOException {
        try (InputStream a = open(jar, resource); InputStream b = Files.newInputStream(target)) {
            byte[] x = new byte[1 << 16], y = new byte[1 << 16];
            for (;;) {
                int n = a.readNBytes(x, 0, x.length), m = b.readNBytes(y, 0, y.length);
                if (n != m || !java.util.Arrays.equals(x, 0, n, y, 0, m)) return false;
                if (n < x.length) return true;
            }
        }
    }

    /** Removes copies set aside by an earlier update, once nothing has them loaded any more. */
    private static void deleteOldCopies(Path target) {
        String prefix = target.getFileName() + ".old-";
        try (var files = Files.list(target.getParent())) {
            files.filter(f -> f.getFileName().toString().startsWith(prefix)).forEach(f -> {
                try {
                    Files.deleteIfExists(f);
                } catch (IOException ignored) {
                    // still loaded; next time
                }
            });
        } catch (IOException ignored) {
            // nothing to clean
        }
    }
}
