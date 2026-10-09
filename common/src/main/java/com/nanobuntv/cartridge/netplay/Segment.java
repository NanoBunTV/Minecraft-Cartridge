package com.nanobuntv.cartridge.netplay;

/** A run of one port's inputs: {@code buttons[i]} is the input for frame {@code start + i} ({@link InputLog}). */
public record Segment(int port, int start, int[] buttons) {
    public int end() {
        return start + buttons.length;
    }
}
