package com.nanobuntv.cartridge.emu;

/**
 * A mouse's input in a port's 16 bits (D144; sidecar/src/protocol.h): bits 0-6 the frame's x movement, bits 7-13 its y
 * movement (each -64..63, two's complement), bit 14 the left button, bit 15 the right. A frame's movement is small
 * enough at 60 frames a second, and it keeps netplay's inputs 16 bits per port.
 */
public final class Mouse {
    public static final int MAX_STEP = 63;
    private static final int LEFT = 1 << 14, RIGHT = 1 << 15;

    private Mouse() {}

    public static int encode(int dx, int dy, boolean left, boolean right) {
        int bits = (clamp(dx) & 0x7F) | (clamp(dy) & 0x7F) << 7;
        if (left) bits |= LEFT;
        if (right) bits |= RIGHT;
        return bits;
    }

    /** The part of a movement one frame can carry. */
    public static int clamp(int d) {
        return Math.clamp(d, -MAX_STEP - 1, MAX_STEP);
    }
}
