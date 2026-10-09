package com.nanobuntv.cartridge.emu;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.util.Random;
import org.junit.jupiter.api.Test;

/** The Pixelon 64 Pad's input word and the deflated savestates (D189). */
class N64PadTest {
    @Test
    void stickAndButtonsShareOneWord() {
        int in = N64Pad.encode(N64Pad.A | N64Pad.Z | N64Pad.C_LEFT, -1F, 1F);
        assertEquals(N64Pad.A | N64Pad.Z | N64Pad.C_LEFT, in & 0xFFFF);
        assertEquals(-128, N64Pad.stickX(in));
        assertEquals(127, N64Pad.stickY(in));
        assertEquals(0, N64Pad.encode(0, 0, 0), "a resting pad sends nothing, like the other consoles' (16 bits on the wire)");
        assertEquals(64, N64Pad.stickX(N64Pad.encode(0, 0.5F, 0)));
        assertEquals(127, N64Pad.stickX(N64Pad.encode(0, 3F, 0)), "clamped");
        assertEquals(N64Pad.START, N64Pad.encode(N64Pad.START | 0xF0000, 0, 0), "buttons stay in the low 16 bits");
    }

    @Test
    void statesDeflateAndInflate() throws IOException {
        byte[] state = new byte[16 * 1024 * 1024 + 15]; // a Pixelon 64 savestate's size, mostly empty RDRAM
        byte[] noise = new byte[4096];
        new Random(64).nextBytes(noise);
        System.arraycopy(noise, 0, state, 0, noise.length);
        for (int i = 0; i < 65536; i++) state[i * 7] = (byte) i;
        byte[] small = States.deflate(state);
        assertEquals(true, small.length < state.length / 8, "deflated to " + small.length);
        assertArrayEquals(state, States.inflate(small));
        assertThrows(IOException.class, () -> States.inflate(java.util.Arrays.copyOf(small, small.length / 2)));
    }
}
