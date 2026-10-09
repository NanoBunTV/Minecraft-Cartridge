package com.nanobuntv.cartridge.rom;

import java.nio.charset.StandardCharsets;

/**
 * A tiny Game Boy program written here, for the tests and the dev self-test (no game files may be downloaded, D59): a
 * 32 KB cartridge marked as also running on the Color (a black cart, D170). On a Color it fills the screen red, then
 * green once Start is pressed; on the original and the Super Game Boy, light, then dark. Its header is a real one with
 * a correct checksum, but it has no Nintendo logo (SameBoy's own boot ROMs don't check it).
 */
public final class GameBoyTestRom {
    private static final int SIZE = 0x8000;

    private GameBoyTestRom() {}

    public static byte[] build() {
        return build(false);
    }

    /**
     * The same program on an MBC1 cartridge with 8 KB of battery RAM, which it never touches: for battery saves (the
     * linked Game Boys', D177).
     */
    public static byte[] withBattery() {
        return build(true);
    }

    private static byte[] build(boolean battery) {
        byte[] rom = new byte[SIZE];
        put(rom, 0x100, 0x00, 0xC3, 0x50, 0x01);        // NOP; JP $0150
        put(rom, 0x150,
                0xF3,                                   // DI
                0x31, 0xFE, 0xFF,                       // LD SP, $FFFE
                0xFE, 0x11,                             // CP $11: A is $11 at start on a Color
                0x20, 0x1D,                             // JR NZ, mono ($0175)
                // Color: every background colour red, written during vertical blank (palette RAM is locked while
                // the screen draws).
                0xF0, 0x44, 0xFE, 0x90, 0x20, 0xFA,     // wait: LDH A, (LY); CP 144; JR NZ, wait
                0x3E, 0x80, 0xE0, 0x68,                 // LD A, $80; LDH (BCPS), A: colour 0, auto-increment
                0x06, 0x20,                             // LD B, 32
                0x3E, 0x1F, 0xE0, 0x69,                 // loop: LD A, $1F; LDH (BCPD), A: red, low byte
                0x3E, 0x00, 0xE0, 0x69,                 // LD A, $00; LDH (BCPD), A
                0x05, 0x20, 0xF5,                       // DEC B; JR NZ, loop
                0x0E, 0x01,                             // LD C, 1: a Color
                0x18, 0x08,                             // JR poll ($017B)
                0x00, 0x00,                             // (padding)
                // mono ($0175): every shade white
                0x3E, 0x00, 0xE0, 0x47,                 // LD A, 0; LDH (BGP), A
                0x0E, 0x00,                             // LD C, 0
                // poll ($017B): until Start
                0x3E, 0x10, 0xE0, 0x00,                 // LD A, $10; LDH (P1), A: the buttons
                0xF0, 0x00, 0xF0, 0x00,                 // LDH A, (P1) twice, to settle
                0xE6, 0x08,                             // AND 8: Start (pressed reads 0)
                0x20, 0xF4,                             // JR NZ, poll
                0x79, 0xB7,                             // LD A, C; OR A
                0x28, 0x19,                             // JR Z, dark ($01A4)
                0xF0, 0x44, 0xFE, 0x90, 0x20, 0xFA,     // wait for vertical blank
                0x3E, 0x80, 0xE0, 0x68,                 // colour 0, auto-increment
                0x06, 0x20,                             // LD B, 32
                0x3E, 0xE0, 0xE0, 0x69,                 // loop: green ($03E0), low byte
                0x3E, 0x03, 0xE0, 0x69,                 // high byte
                0x05, 0x20, 0xF5,                       // DEC B; JR NZ, loop
                0x18, 0x04,                             // JR done ($01A8)
                // dark ($01A4): every shade black
                0x3E, 0xFF, 0xE0, 0x47,                 // LD A, $FF; LDH (BGP), A
                // done ($01A8)
                0x18, 0xFE);                            // JR $
        text(rom, 0x134, 15, "CARTRIDGE TEST");
        rom[0x143] = (byte) 0x80;                       // also runs in colour on the Color
        rom[0x146] = 0x00;                              // no Super Game Boy functions
        rom[0x147] = (byte) (battery ? 0x03 : 0x00);    // MBC1 + RAM + battery, or ROM only
        rom[0x148] = 0x00;                              // 32 KB
        rom[0x149] = (byte) (battery ? 0x02 : 0x00);    // 8 KB of RAM, or none
        rom[0x14A] = 0x01;                              // overseas
        int sum = 0;
        for (int i = 0x134; i <= 0x14C; i++) sum = sum - (rom[i] & 0xFF) - 1;
        rom[0x14D] = (byte) sum;
        int global = 0;
        for (int i = 0; i < SIZE; i++) if (i != 0x14E && i != 0x14F) global += rom[i] & 0xFF;
        rom[0x14E] = (byte) (global >> 8);
        rom[0x14F] = (byte) global;
        return rom;
    }

    private static void put(byte[] rom, int at, int... bytes) {
        for (int i = 0; i < bytes.length; i++) rom[at + i] = (byte) bytes[i];
    }

    private static void text(byte[] rom, int at, int length, String s) {
        byte[] bytes = String.format("%-" + length + "s", s).getBytes(StandardCharsets.US_ASCII);
        for (int i = 0; i < length; i++) rom[at + i] = bytes[i] == ' ' && i >= s.length() ? 0 : bytes[i];
    }
}
