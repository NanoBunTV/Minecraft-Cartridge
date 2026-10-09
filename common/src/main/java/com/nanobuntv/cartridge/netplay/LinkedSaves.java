package com.nanobuntv.cartridge.netplay;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

/**
 * Battery saves of a linked game (D177), packed into one: for each Game Boy its length (4 bytes, big-endian), then its
 * bytes. Netplay moves a linked game's saves as one battery save; the server splits them onto the two cartridges.
 * Plain Java.
 */
public final class LinkedSaves {
    private LinkedSaves() {}

    public static byte[] pack(List<byte[]> saves) {
        int size = 0;
        for (byte[] save : saves) size += 4 + save.length;
        ByteBuffer out = ByteBuffer.allocate(size);
        for (byte[] save : saves) out.putInt(save.length).put(save);
        return out.array();
    }

    /** The saves, or null if {@code packed} isn't {@code count} of them. */
    public static List<byte[]> unpack(byte[] packed, int count) {
        ByteBuffer in = ByteBuffer.wrap(packed);
        List<byte[]> saves = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            if (in.remaining() < 4) return null;
            int n = in.getInt();
            if (n < 0 || n > in.remaining()) return null;
            byte[] save = new byte[n];
            in.get(save);
            saves.add(save);
        }
        return in.hasRemaining() ? null : saves;
    }
}
