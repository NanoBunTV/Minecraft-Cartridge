package com.nanobuntv.cartridge.emu;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * A client's ROM cache, {@code <gameDir>/cartridge_mod/rom_cache/<sha1>}: ROMs are stored once by SHA-1 (D16), shared
 * by every world and server. Filled by burning in the Cartridge Writer and by downloads from a server's ROM store.
 */
public final class RomCache {
    private static final Pattern SHA1 = Pattern.compile("[0-9a-f]{40}");

    private RomCache() {}

    public static Path directory(Path gameDir) {
        return gameDir.resolve("cartridge_mod").resolve("rom_cache");
    }

    /** Stores a ROM (if it isn't stored yet) and returns its SHA-1. */
    public static String put(Path gameDir, byte[] rom) throws IOException {
        String sha1 = sha1(rom);
        Path dir = directory(gameDir);
        Path target = dir.resolve(sha1);
        if (!Files.exists(target)) {
            Files.createDirectories(dir);
            Path temp = Files.createTempFile(dir, sha1, ".tmp");
            Files.write(temp, rom);
            Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        }
        return sha1;
    }

    /** The ROM with this SHA-1, or empty if it isn't cached (or the cached file is damaged). */
    public static Optional<byte[]> get(Path gameDir, String sha1) throws IOException {
        if (!SHA1.matcher(sha1).matches()) return Optional.empty();
        Path file = directory(gameDir).resolve(sha1);
        if (!Files.exists(file)) return Optional.empty();
        byte[] rom = Files.readAllBytes(file);
        return sha1(rom).equals(sha1) ? Optional.of(rom) : Optional.empty();
    }

    public static String sha1(byte[] data) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-1").digest(data));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
