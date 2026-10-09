package com.nanobuntv.cartridge.emu;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.zip.DataFormatException;
import java.util.zip.Deflater;
import java.util.zip.Inflater;

/**
 * Deflated savestates (D189): a Pixelon 64 savestate is 16 MiB, mostly empty RDRAM, and about 2 MiB deflated. Every
 * client deflates the same way, so the network, Memory Cards and resume files only ever see the small form.
 */
public final class States {
    /** No savestate inflates past this (a Pixelon 64's is just over 16 MiB). */
    private static final int MAX_INFLATED = 64 * 1024 * 1024;

    private States() {}

    public static byte[] deflate(byte[] state) {
        Deflater deflater = new Deflater(Deflater.BEST_SPEED);
        try {
            deflater.setInput(state);
            deflater.finish();
            ByteArrayOutputStream out = new ByteArrayOutputStream(state.length / 6 + 64);
            byte[] buf = new byte[1 << 16];
            while (!deflater.finished()) out.write(buf, 0, deflater.deflate(buf));
            return out.toByteArray();
        } finally {
            deflater.end();
        }
    }

    public static byte[] inflate(byte[] deflated) throws IOException {
        Inflater inflater = new Inflater();
        try {
            inflater.setInput(deflated);
            ByteArrayOutputStream out = new ByteArrayOutputStream(deflated.length * 8);
            byte[] buf = new byte[1 << 16];
            while (!inflater.finished()) {
                int n = inflater.inflate(buf);
                if (n == 0 && (inflater.needsInput() || inflater.needsDictionary())) throw new IOException("truncated savestate");
                out.write(buf, 0, n);
                if (out.size() > MAX_INFLATED) throw new IOException("savestate too large");
            }
            return out.toByteArray();
        } catch (DataFormatException e) {
            throw new IOException("corrupt savestate", e);
        } finally {
            inflater.end();
        }
    }
}
