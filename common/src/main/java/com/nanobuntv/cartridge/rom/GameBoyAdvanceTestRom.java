package com.nanobuntv.cartridge.rom;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;

/**
 * A tiny Game Boy Advance program written here, for the tests and the dev self-test (no game files may be downloaded,
 * D59): 64 KB of ARM code that fills the screen red, then green while Start is held, and writes a byte to its battery
 * save (marked "SRAM_V" so mGBA gives it 32 KB of battery RAM). Its header is a real one with a correct complement
 * check, but it has no Nintendo logo (mGBA's own BIOS doesn't check it).
 */
public final class GameBoyAdvanceTestRom {
    private static final int SIZE = 0x10000;
    /** Assembled with zig cc (arm7tdmi); the source is beside each word. Starts at 0xC0. */
    private static final int[] CODE = {
            0xE3A00301, // mov r0, #0x04000000        the I/O registers
            0xE3A01B01, // mov r1, #0x400
            0xE3811003, // orr r1, r1, #3              mode 3, background 2
            0xE1C010B0, // strh r1, [r0]               DISPCNT
            0xE3A0240E, // mov r2, #0x0E000000         battery RAM
            0xE3A0305A, // mov r3, #0x5A
            0xE5C23000, // strb r3, [r2]
            0xE3A0801F, // mov r8, #0x1F               red
            0xE3A09E3E, // mov r9, #0x3E0              green
            0xE280AE13, // add r10, r0, #0x130         KEYINPUT
            0xE1DA40B0, // loop: ldrh r4, [r10]
            0xE3140008, // tst r4, #8                  Start (pressed reads 0)
            0x01A05009, // moveq r5, r9
            0x11A05008, // movne r5, r8
            0xE3A06406, // mov r6, #0x06000000         the picture
            0xE3A07C96, // mov r7, #0x9600             240 x 160 pixels
            0xE0C650B2, // fill: strh r5, [r6], #2
            0xE2577001, // subs r7, r7, #1
            0x1AFFFFFC, // bne fill
            0xEAFFFFF5, // b loop
    };

    private GameBoyAdvanceTestRom() {}

    public static byte[] build() {
        byte[] rom = new byte[SIZE];
        ByteBuffer b = ByteBuffer.wrap(rom).order(ByteOrder.LITTLE_ENDIAN);
        b.putInt(0, 0xEA00002E);                        // b 0xC0
        for (int i = 0; i < CODE.length; i++) b.putInt(0xC0 + i * 4, CODE[i]);
        put(rom, 0xA0, "CARTRIDGE");                    // title, 12 bytes
        put(rom, 0xAC, "ZCTE");                         // game code
        put(rom, 0xB0, "00");                           // maker
        rom[0xB2] = (byte) 0x96;                        // fixed
        int sum = 0;
        for (int i = 0xA0; i <= 0xBC; i++) sum -= rom[i] & 0xFF;
        rom[0xBD] = (byte) (sum - 0x19);                // complement check
        put(rom, 0x200, "SRAM_V113");                   // the save type mGBA looks for
        return rom;
    }

    private static void put(byte[] rom, int at, String s) {
        byte[] bytes = s.getBytes(StandardCharsets.US_ASCII);
        System.arraycopy(bytes, 0, rom, at, bytes.length);
    }
}
