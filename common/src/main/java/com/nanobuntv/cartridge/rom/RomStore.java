package com.nanobuntv.cartridge.rom;

import com.nanobuntv.cartridge.emu.RomCache;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * The server's ROM store (D14, D16): {@code <world>/cartridge_mod/roms/<sha1>}, each game stored once however many
 * cartridges hold it, and the shared BIOS folder {@code <world>/cartridge_mod/bios/<file>} (D74). Nothing is deleted
 * automatically (D71): cartridges can sit in unloaded chests, so the server can't know which ROMs are unused. The
 * quota counts the ROMs only; it's checked by the caller ({@link #usedBytes()}). Files may still vanish (removed by
 * hand): callers treat a missing file as a missing game (D124-D126). Server thread only, but {@link #verify}.
 */
public final class RomStore {
    private static final Pattern SHA1 = Pattern.compile("[0-9a-f]{40}");

    private final Path roms, bios;
    private long used;

    public RomStore(Path root) throws IOException {
        this.roms = root.resolve("roms");
        this.bios = root.resolve("bios");
        this.used = scan();
    }

    private long scan() throws IOException {
        if (!Files.isDirectory(roms)) return 0;
        try (Stream<Path> files = Files.list(roms)) {
            long total = 0;
            for (Path file : (Iterable<Path>) files::iterator) {
                if (SHA1.matcher(file.getFileName().toString()).matches()) total += Files.size(file);
            }
            return total;
        }
    }

    /** Bytes taken by stored ROMs (counted at startup and as ROMs are added). */
    public long usedBytes() {
        return used;
    }

    public boolean hasRom(String sha1) {
        return romFile(sha1).filter(Files::isRegularFile).isPresent();
    }

    /** The stored file for a ROM SHA-1 (which may not exist); empty for anything that isn't a SHA-1. */
    public Optional<Path> romFile(String sha1) {
        return SHA1.matcher(sha1).matches() ? Optional.of(roms.resolve(sha1)) : Optional.empty();
    }

    /** The first bytes of a stored ROM (enough for {@link RomFormats#detect}); empty if it can't be read. */
    public byte[] romHeader(String sha1) {
        Optional<Path> file = romFile(sha1);
        if (file.isEmpty()) return new byte[0];
        try (InputStream in = Files.newInputStream(file.get())) {
            return in.readNBytes(64);
        } catch (IOException e) {
            return new byte[0];
        }
    }

    /** The SHA-1s of every stored ROM (D125). */
    public List<String> romHashes() throws IOException {
        List<String> out = new ArrayList<>();
        if (!Files.isDirectory(roms)) return out;
        try (Stream<Path> files = Files.list(roms)) {
            for (Path file : (Iterable<Path>) files::iterator) {
                String name = file.getFileName().toString();
                if (SHA1.matcher(name).matches() && Files.isRegularFile(file)) out.add(name);
            }
        }
        return out;
    }

    /**
     * D126: hashes every stored ROM and BIOS file and moves the ones that don't match their hash into
     * {@code roms/damaged/} or {@code bios/damaged/}, so they count as missing. Returns the moved ROMs' SHA-1s and the
     * BIOS file names. Slow with many large files: call it off the server thread, before anything else touches the
     * store, and {@link #rescan} afterwards on the server thread.
     */
    public List<String> verify() throws IOException {
        List<String> damaged = new ArrayList<>();
        for (String sha1 : romHashes()) {
            Path file = roms.resolve(sha1);
            if (!RomCache.sha1(Files.readAllBytes(file)).equals(sha1)) {
                moveAside(roms, file);
                damaged.add(sha1);
            }
        }
        for (RomFormats.Bios b : RomFormats.allBios()) {
            Path file = biosFile(b);
            if (Files.isRegularFile(file) && !hasBios(b)) {
                moveAside(bios, file);
                damaged.add(b.fileName());
            }
        }
        return damaged;
    }

    /** Counts the stored ROMs again (after {@link #verify}, or files removed by hand). */
    public void rescan() throws IOException {
        used = scan();
    }

    private static void moveAside(Path dir, Path file) throws IOException {
        Path aside = dir.resolve("damaged");
        Files.createDirectories(aside);
        Files.move(file, aside.resolve(file.getFileName().toString() + "-" + System.currentTimeMillis()),
                StandardCopyOption.REPLACE_EXISTING);
    }

    /** Stores a ROM whose SHA-1 the caller has checked. */
    public void putRom(String sha1, byte[] data) throws IOException {
        Path target = romFile(sha1).orElseThrow(() -> new IllegalArgumentException("not a SHA-1: " + sha1));
        if (Files.exists(target)) return;
        write(roms, target, data);
        used += data.length;
    }

    public Path biosFile(RomFormats.Bios b) {
        return bios.resolve(b.fileName());
    }

    /** True if this BIOS is stored and intact. */
    public boolean hasBios(RomFormats.Bios b) {
        Path file = biosFile(b);
        try {
            return Files.isRegularFile(file) && Files.size(file) == b.size()
                    && RomCache.sha1(Files.readAllBytes(file)).equals(b.sha1());
        } catch (IOException e) {
            return false;
        }
    }

    /** Stores a BIOS whose SHA-1 the caller has checked against {@code b}. */
    public void putBios(RomFormats.Bios b, byte[] data) throws IOException {
        write(bios, biosFile(b), data);
    }

    private static void write(Path dir, Path target, byte[] data) throws IOException {
        Files.createDirectories(dir);
        Path temp = Files.createTempFile(dir, target.getFileName().toString(), ".tmp");
        try {
            Files.write(temp, data);
            Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } finally {
            Files.deleteIfExists(temp);
        }
    }
}
