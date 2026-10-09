package com.nanobuntv.cartridge.rom;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nanobuntv.cartridge.item.CartridgeContents;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class RomFormatsTest {
    @Test
    void detectsByHeader() {
        assertEquals(Optional.of(CartridgeContents.NES), RomFormats.detect(withMagic("NES\u001a")));
        assertEquals(Optional.of(CartridgeContents.FDS), RomFormats.detect(withMagic("FDS\u001a")));
        assertEquals(Optional.of(CartridgeContents.FDS), RomFormats.detect(withMagic("\u0001*NINTENDO-HVC*")));
        assertTrue(RomFormats.detect(new byte[64]).isEmpty());
        assertTrue(RomFormats.detect(withMagic("NES")).isEmpty());
        // Game Boy Advance (D183): the fixed byte and the header's complement check; a broken check is no GBA ROM.
        byte[] gba = GameBoyAdvanceTestRom.build();
        assertEquals(Optional.of(CartridgeContents.GAME_BOY_ADVANCE), RomFormats.detect(gba));
        gba[0xBD]++;
        assertTrue(RomFormats.detect(gba).isEmpty());
        assertEquals(CartridgeContents.GAME_BOY_ADVANCE, RomFormats.fromFileName("Game (USA).gba"));
        assertEquals("game.gba", RomFormats.gameFileName(CartridgeContents.GAME_BOY_ADVANCE));
        // SNES (D136): the internal header, with or without a copier's 512-byte header in front.
        byte[] snes = SnesTestRom.build();
        assertEquals(Optional.of(CartridgeContents.SNES), RomFormats.detect(snes));
        byte[] copier = new byte[snes.length + 512];
        System.arraycopy(snes, 0, copier, 512, snes.length);
        assertEquals(Optional.of(CartridgeContents.SNES), RomFormats.detect(copier));
        assertTrue(RomFormats.detect(new byte[0x8000]).isEmpty());
        // Genesis (D151): "SEGA" at 0x100, plain or in a Super Magic Drive dump.
        byte[] genesis = GenesisTestRom.build();
        assertEquals(Optional.of(CartridgeContents.GENESIS), RomFormats.detect(genesis));
        assertEquals(Optional.of(CartridgeContents.GENESIS), RomFormats.detect(smd(genesis)));
        // Game Boy (D170): the header checksum, and byte 0x143 for the cart's kind.
        byte[] gameBoy = GameBoyTestRom.build();
        assertEquals(Optional.of(CartridgeContents.GAME_BOY_DUAL), RomFormats.detect(gameBoy));
        assertEquals(Optional.of(CartridgeContents.GAME_BOY), RomFormats.detect(colorFlag(gameBoy, 0x00)));
        assertEquals(Optional.of(CartridgeContents.GAME_BOY_COLOR), RomFormats.detect(colorFlag(gameBoy, 0xC0)));
        byte[] badChecksum = gameBoy.clone();
        badChecksum[0x14D]++;
        assertTrue(RomFormats.detect(badChecksum).isEmpty());
    }

    /** The Game Boy ROM with another colour flag, and its header checksum put right. */
    private static byte[] colorFlag(byte[] rom, int flag) {
        byte[] out = rom.clone();
        out[0x143] = (byte) flag;
        int sum = 0;
        for (int i = 0x134; i <= 0x14C; i++) sum = sum - (out[i] & 0xFF) - 1;
        out[0x14D] = (byte) sum;
        return out;
    }

    /** A Super Magic Drive dump: a 512-byte header, then each 16 KB block's odd bytes, then its even bytes. */
    private static byte[] smd(byte[] rom) {
        byte[] out = new byte[512 + rom.length];
        out[0] = (byte) (rom.length / 16384);
        out[1] = 3;
        out[8] = (byte) 0xAA;
        out[9] = (byte) 0xBB;
        for (int block = 0; block < rom.length; block += 16384) {
            for (int i = 0; i < 8192; i++) {
                out[512 + block + i] = rom[block + i * 2 + 1];
                out[512 + block + 8192 + i] = rom[block + i * 2];
            }
        }
        return out;
    }

    @Test
    void guessesFromFileNamesAndKnowsTheBios() {
        assertEquals(CartridgeContents.FDS, RomFormats.fromFileName("Zelda.FDS"));
        assertEquals(CartridgeContents.NES, RomFormats.fromFileName("game.unknown"));
        assertEquals(CartridgeContents.GENESIS, RomFormats.fromFileName("Sonic the Hedgehog (USA, Europe).md"));
        assertEquals(CartridgeContents.GENESIS, RomFormats.fromFileName("game.BIN"));
        assertEquals(CartridgeContents.GENESIS, RomFormats.fromFileName("game.gen"));
        assertEquals("game.md", RomFormats.gameFileName(CartridgeContents.GENESIS));
        assertEquals(CartridgeContents.SNES, RomFormats.fromFileName("Mario Paint (USA).sfc"));
        assertEquals(CartridgeContents.SNES, RomFormats.fromFileName("game.SMC"));
        assertEquals("game.sfc", RomFormats.gameFileName(CartridgeContents.SNES));
        assertEquals(CartridgeContents.GAME_BOY, RomFormats.fromFileName("Tetris (World).gb"));
        assertEquals(CartridgeContents.GAME_BOY_COLOR, RomFormats.fromFileName("game.GBC"));
        assertEquals("game.gb", RomFormats.gameFileName(CartridgeContents.GAME_BOY));
        assertEquals("game.gbc", RomFormats.gameFileName(CartridgeContents.GAME_BOY_DUAL));
        assertEquals(Optional.of(RomFormats.FDS_BIOS), RomFormats.bios(CartridgeContents.FDS));
        assertTrue(RomFormats.bios(CartridgeContents.NES).isEmpty());
        assertEquals(Optional.of(RomFormats.FDS_BIOS), RomFormats.biosBySha1(RomFormats.FDS_BIOS.sha1()));
    }

    @Test
    void nintendo64InEveryByteOrder() {
        byte[] z64 = new byte[0x100000];
        byte[] word = {(byte) 0x80, 0x37, 0x12, 0x40};
        System.arraycopy(word, 0, z64, 0, 4);
        for (int i = 4; i < z64.length; i++) z64[i] = (byte) (i * 7);
        byte[] v64 = z64.clone(), n64 = z64.clone();
        for (int i = 0; i < v64.length; i += 2) {
            v64[i] = z64[i + 1];
            v64[i + 1] = z64[i];
        }
        for (int i = 0; i < n64.length; i += 4) {
            for (int k = 0; k < 4; k++) n64[i + k] = z64[i + 3 - k];
        }
        for (byte[] rom : new byte[][] {z64, v64, n64}) {
            assertEquals(Optional.of(CartridgeContents.N64), RomFormats.detect(rom));
            assertArrayEquals(z64, RomFormats.normalize(rom.clone()), "burned as .z64");
        }
        assertEquals(Optional.empty(), RomFormats.detect(java.util.Arrays.copyOf(z64, 0x80000)), "too small");
        byte[] nes = withMagic("NES\u001A");
        assertArrayEquals(nes, RomFormats.normalize(nes.clone()), "other systems stay as they are");
        assertEquals(CartridgeContents.N64, RomFormats.fromFileName("Super Mario 64 (USA).z64"));
        assertEquals(CartridgeContents.N64, RomFormats.fromFileName("game.V64"));
        assertEquals("game.z64", RomFormats.gameFileName(CartridgeContents.N64));
        assertTrue(RomFormats.isKnown(CartridgeContents.N64));
    }

    @Test
    void titlesFromFileNames() {
        assertEquals("Super Mario Bros. 2", RomFormats.titleFromFileName("Super Mario Bros. 2 (USA) (Rev 1).nes"));
        assertEquals("R.C. Pro-Am", RomFormats.titleFromFileName("R.C. Pro-Am (U) (PRG1) [!].nes"));
        assertEquals("The Legend of Zelda", RomFormats.titleFromFileName("Legend of Zelda, The (USA).nes"));
        assertEquals("The Legend of Zelda - Special", RomFormats.titleFromFileName("Legend of Zelda, The - Special (Japan).fds"));
        assertEquals("A Boy and His Blob", RomFormats.titleFromFileName("Boy and His Blob, A [!].nes"));
        assertEquals("Tetris", RomFormats.titleFromFileName("Tetris.nes"));
        assertEquals("(Hack)", RomFormats.titleFromFileName("(Hack).nes"));
    }

    private static byte[] withMagic(String magic) {
        byte[] data = new byte[64];
        byte[] m = magic.getBytes(StandardCharsets.ISO_8859_1);
        System.arraycopy(m, 0, data, 0, m.length);
        return data;
    }
}
