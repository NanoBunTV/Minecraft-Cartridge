package com.nanobuntv.cartridge.save;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SaveStoreTest {
    @TempDir
    Path dir;

    @Test
    void batterySavesTravelWithTheirCart() throws Exception {
        SaveStore store = new SaveStore(dir);
        UUID cart = UUID.randomUUID(), copy = UUID.randomUUID();
        assertTrue(store.readSram(cart).isEmpty());

        byte[] save = new byte[8192];
        save[0] = 7;
        store.writeSram(cart, save);
        assertArrayEquals(save, store.readSram(cart).orElseThrow());
        assertEquals(8192, Files.size(dir.resolve("sram").resolve(cart + ".sav")), "a plain .sav, as other emulators use (D97)");

        // A copy starts with a snapshot; after that they're separate (D73).
        store.copySram(cart, copy);
        save[0] = 8;
        store.writeSram(cart, save);
        assertEquals(7, store.readSram(copy).orElseThrow()[0]);
        assertEquals(8, store.readSram(cart).orElseThrow()[0]);

        store.deleteSram(cart); // erased (D78)
        assertTrue(store.readSram(cart).isEmpty());
        assertThrows(IOException.class, () -> store.writeSram(cart, new byte[SaveStore.MAX_SRAM + 1]));
        try (var files = Files.list(dir.resolve("sram"))) {
            assertTrue(files.noneMatch(f -> f.toString().endsWith(".tmp")), "no temp files left behind");
        }
    }

    @Test
    void memoryCardsRoundTrip() throws Exception {
        SaveStore store = new SaveStore(dir);
        UUID card = UUID.randomUUID();
        assertTrue(store.readCard(card).isEmpty());
        byte[] state = {1, 2, 3, 4, 5};
        store.writeCard(card, new SaveStore.CardState("abc123", "core-sha", 1_700_000_000_000L, state));
        SaveStore.CardState read = store.readCard(card).orElseThrow();
        assertEquals("abc123", read.romSha1());
        assertEquals("core-sha", read.core());
        assertEquals(1_700_000_000_000L, read.savedAt());
        assertArrayEquals(state, read.state());

        Files.write(store.cardFile(card), new byte[] {0, 1, 2});
        assertTrue(store.readCard(card).isEmpty(), "a damaged file reads as no save");
    }
}
