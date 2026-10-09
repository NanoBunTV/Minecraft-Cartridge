package com.nanobuntv.cartridge.rom;

import java.nio.charset.StandardCharsets;

/**
 * A tiny Genesis program written here, for the tests and the dev self-test (no game files may be downloaded, D59): a
 * 64 KB cartridge that fills the screen red, turns it green once Start is pressed on pad 1, then blue once Z is
 * pressed (Z is read the 6-button pad's way, D152). Its header is a real one, with a correct checksum.
 */
public final class GenesisTestRom {
    private static final int SIZE = 0x10000, MAIN = 0x200, SET_COLOR = 0x400, WAIT_VBLANK = 0x410;

    private GenesisTestRom() {}

    /** 68000 code, a word at a time. */
    private static final class Code {
        private final byte[] rom;
        private int at;

        Code(byte[] rom, int at) {
            this.rom = rom;
            this.at = at;
        }

        Code w(int... words) {
            for (int word : words) {
                rom[at++] = (byte) (word >> 8);
                rom[at++] = (byte) word;
            }
            return this;
        }

        /** A short branch back to {@code target} (BNE.S is 0x6600, BEQ.S 0x6700, BRA.S 0x6000). */
        Code branch(int opcode, int target) {
            return w(opcode | (target - (at + 2)) & 0xFF);
        }

        Code jsr(int address) {
            return w(0x4EB9, address >> 16, address & 0xFFFF);
        }

        /** Sets pad 1's TH line, then waits for the pad to settle. */
        Code th(boolean high) {
            w(0x12BC, high ? 0x40 : 0x00); // MOVE.B #x, (A1)
            for (int i = 0; i < 4; i++) w(0x4E71); // NOP
            return this;
        }
    }

    public static byte[] build() {
        byte[] rom = new byte[SIZE];
        // Vectors: the stack at the top of work RAM, everything else at the program's start (interrupts stay off).
        put32(rom, 0, 0x00FFFE00);
        for (int v = 4; v < 0x100; v += 4) put32(rom, v, MAIN);

        Code c = new Code(rom, MAIN);
        c.w(0x46FC, 0x2700);                        // MOVE #$2700, SR: interrupts off
        c.w(0x41F9, 0x00C0, 0x0004);                // LEA $C00004, A0: VDP control
        c.w(0x45F9, 0x00C0, 0x0000);                // LEA $C00000, A2: VDP data
        c.w(0x43F9, 0x00A1, 0x0003);                // LEA $A10003, A1: pad 1 data
        c.w(0x1039, 0x00A1, 0x0001);                // MOVE.B $A10001, D0: hardware version
        c.w(0x0200, 0x000F);                        // ANDI.B #$0F, D0
        c.w(0x670A);                                // BEQ.S over the next instruction
        c.w(0x23FC, 0x5345, 0x4741, 0x00A1, 0x4000); // MOVE.L #'SEGA', $A14000: unlock the VDP (TMSS)
        c.w(0x30BC, 0x8004);                        // VDP register 0: normal
        c.w(0x30BC, 0x8144);                        // register 1: display on, Mode 5, no interrupts
        c.w(0x30BC, 0x8700);                        // register 7: backdrop is colour 0
        c.w(0x323C, 0x000E).jsr(SET_COLOR);         // red
        c.w(0x13FC, 0x0040, 0x00A1, 0x0009);        // pad 1's TH line is an output
        int waitStart = c.at;
        c.th(false);
        c.w(0x1011);                                // MOVE.B (A1), D0: ?0SA00DU
        c.w(0x0800, 0x0005);                        // BTST #5, D0: Start (pressed reads 0)
        c.branch(0x6600, waitStart);
        c.w(0x323C, 0x00E0).jsr(SET_COLOR);         // green
        // The 6-button pad: from TH high, the third low-to-high pulse reads ?1CBMXYZ.
        int waitZ = c.at;
        c.th(true).jsr(WAIT_VBLANK);                // a frame with TH high lets the pad's counter reset
        c.th(false).th(true).th(false).th(true).th(false).th(true);
        c.w(0x1011);                                // MOVE.B (A1), D0
        c.w(0x0800, 0x0000);                        // BTST #0, D0: Z
        c.branch(0x6600, waitZ);
        c.w(0x323C, 0x0E00).jsr(SET_COLOR);         // blue
        c.branch(0x6000, c.at);                     // BRA.S *: done

        Code setColor = new Code(rom, SET_COLOR);   // colour 0 = D1 (----BBB-GGG-RRR-)
        setColor.w(0x20BC, 0xC000, 0x0000);         // MOVE.L #$C0000000, (A0): CRAM write at 0
        setColor.w(0x3481);                         // MOVE.W D1, (A2)
        setColor.w(0x4E75);                         // RTS
        Code wait = new Code(rom, WAIT_VBLANK);     // until the next vertical blank starts
        int outside = wait.at;
        wait.w(0x3010, 0x0800, 0x0003);             // MOVE.W (A0), D0; BTST #3, D0: in vblank?
        wait.branch(0x6600, outside);
        int inside = wait.at;
        wait.w(0x3010, 0x0800, 0x0003);
        wait.branch(0x6700, inside);
        wait.w(0x4E75);

        text(rom, 0x100, 16, "SEGA GENESIS");
        text(rom, 0x110, 16, "(C)CART 2026.OCT");
        text(rom, 0x120, 48, "CARTRIDGE SELF-TEST");
        text(rom, 0x150, 48, "CARTRIDGE SELF-TEST");
        text(rom, 0x180, 14, "GM 00000000-00");
        text(rom, 0x190, 16, "J6");                 // 3- and 6-button pads
        put32(rom, 0x1A0, 0);
        put32(rom, 0x1A4, SIZE - 1);
        put32(rom, 0x1A8, 0x00FF0000);
        put32(rom, 0x1AC, 0x00FFFFFF);
        text(rom, 0x1B0, 64, "");                   // no save RAM, no modem, no notes
        text(rom, 0x1F0, 16, "JUE");
        // The checksum: every word after the header, added up.
        int sum = 0;
        for (int i = 0x200; i < SIZE; i += 2) sum += (rom[i] & 0xFF) << 8 | rom[i + 1] & 0xFF;
        rom[0x18E] = (byte) (sum >> 8);
        rom[0x18F] = (byte) sum;
        return rom;
    }

    private static void put32(byte[] rom, int at, int value) {
        for (int i = 0; i < 4; i++) rom[at + i] = (byte) (value >> (24 - 8 * i));
    }

    private static void text(byte[] rom, int at, int length, String s) {
        byte[] bytes = String.format("%-" + length + "s", s).getBytes(StandardCharsets.US_ASCII);
        System.arraycopy(bytes, 0, rom, at, length);
    }
}
