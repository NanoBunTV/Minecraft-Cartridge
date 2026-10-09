package com.nanobuntv.cartridge.netplay;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.List;
import org.junit.jupiter.api.Test;

class LinkedSavesTest {
    /** A linked game's two battery saves (D177) survive packing, an empty one included; damaged packs are refused. */
    @Test
    void packsAndUnpacks() {
        byte[] first = {1, 2, 3}, second = {};
        byte[] packed = LinkedSaves.pack(List.of(first, second));
        List<byte[]> back = LinkedSaves.unpack(packed, 2);
        assertEquals(2, back.size());
        assertArrayEquals(first, back.get(0));
        assertArrayEquals(second, back.get(1));
        assertNull(LinkedSaves.unpack(packed, 3), "too few");
        assertNull(LinkedSaves.unpack(java.util.Arrays.copyOf(packed, packed.length + 1), 2), "bytes left over");
        assertNull(LinkedSaves.unpack(new byte[] {0, 0, 1, 0}, 1), "a length past the end");
    }
}
