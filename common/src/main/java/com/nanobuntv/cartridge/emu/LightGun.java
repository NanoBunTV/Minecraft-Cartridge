package com.nanobuntv.cartridge.emu;

/**
 * A light gun's input in a port's 16 bits (D99; sidecar/src/protocol.h): bits 0-7 the picture's x (0-255), bits 8-14
 * y / 2 (0-119; {@link #OFFSCREEN_Y} when aimed off the screen), bit 15 the trigger. Half the vertical resolution is
 * far finer than the Zapper's own sensing area, and it keeps netplay's inputs 16 bits per port.
 */
public final class LightGun {
    public static final int WIDTH = 256, HEIGHT = 240;
    private static final int OFFSCREEN_Y = 127;
    private static final int TRIGGER = 0x8000;

    private LightGun() {}

    /** Aimed at picture pixel ({@code x}, {@code y}); outside 0..255 x 0..239 counts as off the screen. */
    public static int encode(int x, int y, boolean trigger) {
        int bits = x < 0 || x >= WIDTH || y < 0 || y >= HEIGHT ? OFFSCREEN_Y << 8 : x | (y / 2) << 8;
        return trigger ? bits | TRIGGER : bits;
    }

    /** Aimed off the screen (at the wall, or no TV in reach). */
    public static int offscreen(boolean trigger) {
        return encode(-1, -1, trigger);
    }

    public static boolean trigger(int bits) {
        return (bits & TRIGGER) != 0;
    }
}
