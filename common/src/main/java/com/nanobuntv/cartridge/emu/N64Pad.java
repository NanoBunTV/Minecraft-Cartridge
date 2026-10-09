package com.nanobuntv.cartridge.emu;

/**
 * A Pixelon 64 Pad's input in a port's 32 bits (D189; sidecar/src/protocol.h): the buttons in the low 16, as
 * Mupen64Plus-Next's alternate mapping reads the RetroPad ({@code mupen64plus-alt-map}), and the control stick in the
 * high 16, x then y, each -128..127 (y grows downwards, as libretro's does).
 */
public final class N64Pad {
    // N64 button -> the RetroPad bit (RETRO_DEVICE_ID_JOYPAD_*) the alternate mapping reads it from.
    public static final int A = 1, B = 1 << 1, L = 1 << 2, START = 1 << 3, UP = 1 << 4, DOWN = 1 << 5, LEFT = 1 << 6,
            RIGHT = 1 << 7, C_DOWN = 1 << 8, C_UP = 1 << 9, C_LEFT = 1 << 10, C_RIGHT = 1 << 11, Z = 1 << 12, R = 1 << 13;

    private N64Pad() {}

    /** Buttons and a stick position, each axis -1..1 (y down). */
    public static int encode(int buttons, float x, float y) {
        return buttons & 0xFFFF | (axis(x) & 0xFF) << 16 | (axis(y) & 0xFF) << 24;
    }

    private static int axis(float v) {
        return Math.round(Math.clamp(v, -1F, 1F) * (v < 0 ? 128 : 127));
    }

    public static int stickX(int input) {
        return (byte) (input >> 16);
    }

    public static int stickY(int input) {
        return (byte) (input >> 24);
    }
}
