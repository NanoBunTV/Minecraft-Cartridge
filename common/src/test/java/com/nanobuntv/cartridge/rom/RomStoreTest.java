package com.nanobuntv.cartridge.rom;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nanobuntv.cartridge.emu.RomCache;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RomStoreTest {
    @TempDir
    Path root;

    @Test
    void storesRomsOnceAndCountsThem() throws Exception {
        RomStore store = new RomStore(root);
        byte[] rom = new byte[100];
        String sha1 = RomCache.sha1(rom);
        assertFalse(store.hasRom(sha1));
        store.putRom(sha1, rom);
        store.putRom(sha1, rom);
        assertTrue(store.hasRom(sha1));
        assertEquals(100, store.usedBytes());
        assertEquals(100, new RomStore(root).usedBytes()); // counted again at startup
        assertTrue(store.romFile("../../level.dat").isEmpty());
    }

    @Test
    void checksTheStoredBios() throws Exception {
        RomStore store = new RomStore(root);
        RomFormats.Bios fake = new RomFormats.Bios("fds", "disksys.rom", RomCache.sha1(new byte[8]), 8);
        assertFalse(store.hasBios(fake));
        store.putBios(fake, new byte[8]);
        assertTrue(store.hasBios(fake));
        Files.write(store.biosFile(fake), new byte[] {1, 2, 3, 4, 5, 6, 7, 8}); // damaged
        assertFalse(store.hasBios(fake));
    }

    /** D125, D126: the store lists its games, and the startup check moves damaged files aside. */
    @Test
    void listsGamesAndMovesDamagedFilesAside() throws Exception {
        RomStore store = new RomStore(root);
        byte[] good = new byte[64], bad = new byte[80];
        good[0] = 1;
        bad[0] = 2;
        String goodSha = RomCache.sha1(good), badSha = RomCache.sha1(bad);
        store.putRom(goodSha, good);
        store.putRom(badSha, bad);
        assertEquals(java.util.Set.of(goodSha, badSha), java.util.Set.copyOf(store.romHashes()));

        Files.write(store.romFile(badSha).orElseThrow(), new byte[] {9, 9, 9}); // damaged on disk
        assertEquals(java.util.List.of(badSha), store.verify());
        store.rescan();
        assertFalse(store.hasRom(badSha));
        assertTrue(store.hasRom(goodSha));
        assertEquals(64, store.usedBytes());
        assertTrue(Files.isDirectory(root.resolve("roms").resolve("damaged")));
    }
}
