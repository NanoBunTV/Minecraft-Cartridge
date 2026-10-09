package com.nanobuntv.cartridge.netplay;

import java.util.Arrays;

/**
 * The known inputs of each port, frame by frame, from a base frame on (D12): a joypad bitmask, a light gun's aim or a
 * mouse's movement in the low 16 bits, a Pixelon 64 Pad's stick in the high 16 (D189). Each port's inputs are
 * contiguous: new ones are only ever added at {@link #next(int)}. Used by the server's timeline and each client's
 * rollback engine.
 */
public final class InputLog {
    private int base;
    private final int[][] data;
    private final int[] size;

    public InputLog(int base, int ports) {
        this.base = base;
        this.data = new int[ports][];
        this.size = new int[ports];
        for (int p = 0; p < ports; p++) data[p] = new int[256];
    }

    public int ports() {
        return data.length;
    }

    public int base() {
        return base;
    }

    /** The first frame of {@code port} that isn't known yet. */
    public int next(int port) {
        return base + size[port];
    }

    /** The last frame every port is known for (base - 1 when nothing is). */
    public int confirmed() {
        int c = Integer.MAX_VALUE;
        for (int p = 0; p < data.length; p++) c = Math.min(c, next(p) - 1);
        return c;
    }

    public int maxNext() {
        int m = base;
        for (int p = 0; p < data.length; p++) m = Math.max(m, next(p));
        return m;
    }

    public boolean has(int port, int frame) {
        return frame >= base && frame < next(port);
    }

    public int get(int port, int frame) {
        if (!has(port, frame)) throw new IndexOutOfBoundsException("frame " + frame + " of port " + port);
        return data[port][frame - base];
    }

    /** The last known input of a port, or 0 if none is known. */
    public int last(int port) {
        return size[port] > 0 ? data[port][size[port] - 1] : 0;
    }

    /** Appends inputs at {@link #next(int)}; {@code start} must equal it. */
    public void append(int port, int start, int[] values, int from, int count) {
        if (start != next(port)) throw new IllegalArgumentException("port " + port + " continues at " + next(port) + ", not " + start);
        ensure(port, size[port] + count);
        System.arraycopy(values, from, data[port], size[port], count);
        size[port] += count;
    }

    /** Appends {@code count} zero frames. */
    public void appendZeros(int port, int count) {
        ensure(port, size[port] + count);
        Arrays.fill(data[port], size[port], size[port] + count, 0);
        size[port] += count;
    }

    /** A copy of a port's inputs from {@code from} (at least the base) up to {@link #next(int)}. */
    public int[] copy(int port, int from) {
        int start = Math.max(from, base) - base;
        return start >= size[port] ? new int[0] : Arrays.copyOfRange(data[port], start, size[port]);
    }

    /** Forgets everything before {@code frame}; a port that isn't known that far yet starts empty there. */
    public void dropBefore(int frame) {
        if (frame <= base) return;
        int shift = frame - base;
        for (int p = 0; p < data.length; p++) {
            int keep = Math.max(0, size[p] - shift);
            if (keep > 0) System.arraycopy(data[p], shift, data[p], 0, keep);
            size[p] = keep;
        }
        base = frame;
    }

    private void ensure(int port, int capacity) {
        if (capacity > data[port].length) {
            data[port] = Arrays.copyOf(data[port], Math.max(capacity, data[port].length * 2));
        }
    }
}
