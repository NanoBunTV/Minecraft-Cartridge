package com.nanobuntv.cartridge.emu;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * mGBA's real-time clock on emulated time (D186). The core reads the PC's clock for cartridges with one (Pokémon Ruby,
 * Sapphire and Emerald, Boktai...), so players' games and rollback replays would read different times and drift apart.
 * It has no option for that, but its savestates carry the clock's mode and loading one switches to it: the savestate
 * taken right after loading is patched to "fake epoch" (a fixed start, then time as the game has run) and loaded back.
 * Every later savestate carries that on.
 *
 * <p>The layout (mGBA's {@code mCoreSaveStateNamed}): the core's own state, then extra-data headers (tag, size, offset:
 * 4, 4 and 8 bytes, little-endian) ending with tag 0, then the items at their offsets. The clock's item (tag 4) is its
 * mode and padding (4 bytes each), then a value in milliseconds (8 bytes).
 */
public final class MgbaClock {
    /** The clock starts at 2001-03-21 00:00 UTC (the Game Boy Advance's launch day) on every power-on. */
    public static final long START_MILLIS = 985_132_800_000L;
    /** The core's own part of a Game Boy Advance savestate ({@code sizeof(struct GBASerializedState)}). */
    static final int GBA_STATE_SIZE = 0x61000;
    private static final int TAG_RTC = 4, MODE_FAKE_EPOCH = 2, HEADER = 16;

    private MgbaClock() {}

    /** Switches a freshly loaded game's clock to emulated time. */
    public static void useEmulatedTime(HostProcess host) throws IOException {
        byte[] state = host.serialize();
        if (!patch(state, GBA_STATE_SIZE)) throw new IOException("mGBA's savestate has no clock to set");
        host.unserialize(state);
    }

    /**
     * Sets the clock item in a savestate to fake epoch from {@link #START_MILLIS}; false if it has none.
     * {@code coreStateSize} is where the headers start.
     */
    static boolean patch(byte[] state, int coreStateSize) {
        ByteBuffer b = ByteBuffer.wrap(state).order(ByteOrder.LITTLE_ENDIAN);
        for (int at = coreStateSize; at + HEADER <= state.length; at += HEADER) {
            int tag = b.getInt(at), size = b.getInt(at + 4);
            long offset = b.getLong(at + 8);
            if (tag == 0) return false;
            if (tag == TAG_RTC && size >= 16 && offset >= 0 && offset + 16 <= state.length) {
                b.putInt((int) offset, MODE_FAKE_EPOCH);
                b.putInt((int) offset + 4, 0);
                b.putLong((int) offset + 8, START_MILLIS);
                return true;
            }
        }
        return false;
    }

    /** The clock's mode and value in a savestate, or null: for the tests. */
    static long[] read(byte[] state, int coreStateSize) {
        ByteBuffer b = ByteBuffer.wrap(state).order(ByteOrder.LITTLE_ENDIAN);
        for (int at = coreStateSize; at + HEADER <= state.length; at += HEADER) {
            int tag = b.getInt(at);
            long offset = b.getLong(at + 8);
            if (tag == 0) return null;
            if (tag == TAG_RTC) return new long[] {b.getInt((int) offset), b.getLong((int) offset + 8)};
        }
        return null;
    }
}
