package com.nanobuntv.cartridge.emu;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Extraction from the classpath (natives/ and the unpacked cores are on the test classpath, laid out as in a dev run),
 * and cartridge-host's download (D218) from a local server.
 */
class NativesTest {
    @TempDir
    Path gameDir;

    @Test
    void extractsOnceAndRefreshesWhenChanged() throws Exception {
        HostPlatform platform = HostPlatform.current().orElseThrow();
        Path host = Natives.extractHost(gameDir, platform);
        assertEquals(gameDir.resolve("cartridge_mod/natives").resolve(platform.id).resolve(platform.hostFileName), host);
        long size = Files.size(host);
        assertTrue(size > 1000);

        // Unchanged: left alone.
        FileTime old = FileTime.fromMillis(1_000_000_000_000L);
        Files.setLastModifiedTime(host, old);
        Natives.extractHost(gameDir, platform);
        assertEquals(old, Files.getLastModifiedTime(host));

        // Different (an older mod version's host): replaced.
        Files.write(host, new byte[] {1, 2, 3});
        Natives.extractHost(gameDir, platform);
        assertEquals(size, Files.size(host));
    }

    @Test
    void extractsTheBundledCoreOverAnOldDownload() throws Exception {
        HostPlatform platform = HostPlatform.current().orElseThrow();
        Path dir = gameDir.resolve("cartridge_mod/cores").resolve(platform.id);
        Path lib = dir.resolve("fceumm_libretro" + platform.librarySuffix);
        // What the old downloader left behind (before D114).
        Files.createDirectories(dir);
        Files.write(lib, new byte[] {1, 2, 3});
        Files.writeString(dir.resolve(lib.getFileName() + ".sha256"), "abc\n");

        assertEquals(lib, Natives.extractCore(gameDir, platform, CoreSpec.FCEUMM));
        assertTrue(Files.size(lib) > 1_000_000);
        assertTrue(Files.notExists(dir.resolve(lib.getFileName() + ".sha256")));
    }

    /** The first address with the right file wins: a missing tag (404) or a newer build (wrong hash) is skipped. */
    @Test
    void downloadsTheHostWithTheRightHash() throws Exception {
        byte[] right = "the right host".getBytes(StandardCharsets.US_ASCII);
        Path expected = gameDir.resolve("expected");
        Files.write(expected, right);
        String sha = Natives.sha256(expected);
        HttpServer server = serve(right);
        try {
            String base = "http://127.0.0.1:" + server.getAddress().getPort();
            Path target = gameDir.resolve("cartridge_mod/natives/x/cartridge-host");
            Natives.download(List.of(URI.create(base + "/missing"), URI.create(base + "/wrong"), URI.create(base + "/right")),
                    sha, target);
            assertArrayEquals(right, Files.readAllBytes(target));
            try (var files = Files.list(target.getParent())) {
                assertEquals(1, files.count()); // no temp files left
            }
        } finally {
            server.stop(0);
        }
    }

    @Test
    void refusesAHostWithTheWrongHash() throws Exception {
        HttpServer server = serve("the right host".getBytes(StandardCharsets.US_ASCII));
        try {
            String base = "http://127.0.0.1:" + server.getAddress().getPort();
            Path target = gameDir.resolve("cartridge_mod/natives/x/cartridge-host");
            IOException e = assertThrows(IOException.class, () -> Natives.download(
                    List.of(URI.create(base + "/missing"), URI.create(base + "/wrong")), "00".repeat(32), target));
            assertTrue(e.getMessage().contains("isn't the file"), e.getMessage());
            try (var files = Files.list(target.getParent())) {
                assertEquals(0, files.count());
            }
        } finally {
            server.stop(0);
        }
    }

    /** /right serves {@code right}, /wrong something else, anything else 404. */
    private static HttpServer serve(byte[] right) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            String path = exchange.getRequestURI().getPath();
            byte[] body = path.equals("/right") ? right : path.equals("/wrong") ? new byte[] {1, 2, 3} : null;
            exchange.sendResponseHeaders(body == null ? 404 : 200, body == null ? -1 : body.length);
            if (body != null) exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        return server;
    }

    /** Both platforms' libraries are the commit each core's version names, so players on either can play together. */
    @Test
    void everyPlatformCarriesThePinnedCommit() throws Exception {
        for (CoreSpec core : List.of(CoreSpec.FCEUMM, CoreSpec.SNES9X, CoreSpec.GENESIS_PLUS_GX, CoreSpec.SAMEBOY, CoreSpec.MGBA,
                CoreSpec.MUPEN64PLUS_NEXT)) {
            byte[] marker = core.marker().getBytes(StandardCharsets.US_ASCII);
            for (HostPlatform platform : HostPlatform.values()) {
                String resource = "/cores/" + platform.id + "/" + core.fileName(platform);
                try (InputStream in = NativesTest.class.getResourceAsStream(resource)) {
                    assertNotNull(in, resource);
                    assertTrue(contains(in.readAllBytes(), marker), resource + " is not " + core.version());
                }
            }
        }
    }

    private static boolean contains(byte[] data, byte[] marker) {
        outer:
        for (int i = 0; i <= data.length - marker.length; i++) {
            for (int j = 0; j < marker.length; j++) {
                if (data[i + j] != marker[j]) continue outer;
            }
            return true;
        }
        return false;
    }
}
