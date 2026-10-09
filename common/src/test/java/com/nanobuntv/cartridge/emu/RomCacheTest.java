package com.nanobuntv.cartridge.emu;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RomCacheTest {
    @TempDir
    Path gameDir;

    @Test
    void storesByHashAndVerifiesOnRead() throws Exception {
        byte[] rom = new byte[64];
        rom[0] = 'N';
        rom[1] = 'E';
        rom[2] = 'S';
        rom[3] = 0x1A;
        String sha1 = RomCache.put(gameDir, rom);
        assertEquals(40, sha1.length());
        assertEquals(sha1, RomCache.put(gameDir, rom)); // stored once
        assertArrayEquals(rom, RomCache.get(gameDir, sha1).orElseThrow());

        // A damaged file isn't handed out, and odd names can't reach outside the cache.
        Files.write(RomCache.directory(gameDir).resolve(sha1), new byte[] {1});
        assertTrue(RomCache.get(gameDir, sha1).isEmpty());
        assertTrue(RomCache.get(gameDir, "../../etc/passwd").isEmpty());
    }
}
