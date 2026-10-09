package com.nanobuntv.cartridge.rom;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/**
 * A tiny SNES program written here, for the tests and the dev self-test (no game files may be downloaded, D59): a 32 KB
 * LoROM that fills the screen red and turns it green once Start is pressed on pad 1. Its header is a real one, with a
 * correct checksum.
 */
public final class SnesTestRom {
    private static final int SIZE = 0x8000, HEADER = 0x7FC0;

    private SnesTestRom() {}

    public static byte[] build() {
        byte[] rom = new byte[SIZE];
        Arrays.fill(rom, (byte) 0xFF);
        int[] code = {
                0x78,                   // SEI
                0x18, 0xFB,             // CLC, XCE: native mode
                0xE2, 0x30,             // SEP #$30: 8-bit A, X, Y
                0xA9, 0x8F, 0x8D, 0x00, 0x21, // LDA #$8F, STA INIDISP: forced blank
                0x9C, 0x21, 0x21,       // STZ CGADD: colour 0, the backdrop
                0xA9, 0x1F, 0x8D, 0x22, 0x21, // LDA #$1F, STA CGDATA: red, low byte
                0x9C, 0x22, 0x21,       // STZ CGDATA: high byte
                0xA9, 0x0F, 0x8D, 0x00, 0x21, // LDA #$0F, STA INIDISP: screen on, full brightness
                0xA9, 0x01, 0x8D, 0x00, 0x42, // LDA #$01, STA NMITIMEN: read the pads every frame
                // wait: (offset 0x1F)
                0xAD, 0x19, 0x42,       // LDA JOY1H
                0x29, 0x10,             // AND #$10: Start
                0xF0, 0xF9,             // BEQ wait
                0xA9, 0x8F, 0x8D, 0x00, 0x21, // forced blank
                0x9C, 0x21, 0x21,       // STZ CGADD
                0xA9, 0xE0, 0x8D, 0x22, 0x21, // green (0x03E0), low byte
                0xA9, 0x03, 0x8D, 0x22, 0x21, // high byte
                0xA9, 0x0F, 0x8D, 0x00, 0x21, // screen on
                0x80, 0xFE,             // BRA *: done
        };
        for (int i = 0; i < code.length; i++) rom[i] = (byte) code[i];

        byte[] title = String.format("%-21s", "CARTRIDGE SELF-TEST").getBytes(StandardCharsets.US_ASCII);
        System.arraycopy(title, 0, rom, HEADER, 21);
        rom[HEADER + 0x15] = 0x20; // LoROM
        rom[HEADER + 0x16] = 0x00; // ROM only
        rom[HEADER + 0x17] = 0x05; // 32 KB
        rom[HEADER + 0x18] = 0x00; // no RAM
        rom[HEADER + 0x19] = 0x01; // North America
        rom[HEADER + 0x1A] = 0x00;
        rom[HEADER + 0x1B] = 0x00; // version 1.0
        // Every vector points at the code's start; NMI and IRQ stay off.
        for (int v = 0x7FE4; v < 0x8000; v += 2) {
            rom[v] = 0x00;
            rom[v + 1] = (byte) 0x80;
        }
        // The checksum covers the whole ROM with the checksum and its complement counted as 0x0000 and 0xFFFF.
        rom[HEADER + 0x1C] = (byte) 0xFF;
        rom[HEADER + 0x1D] = (byte) 0xFF;
        rom[HEADER + 0x1E] = 0;
        rom[HEADER + 0x1F] = 0;
        int sum = 0;
        for (byte b : rom) sum += b & 0xFF;
        sum &= 0xFFFF;
        int complement = sum ^ 0xFFFF;
        rom[HEADER + 0x1C] = (byte) complement;
        rom[HEADER + 0x1D] = (byte) (complement >> 8);
        rom[HEADER + 0x1E] = (byte) sum;
        rom[HEADER + 0x1F] = (byte) (sum >> 8);
        return rom;
    }
}
