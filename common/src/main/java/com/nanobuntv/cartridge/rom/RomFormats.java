package com.nanobuntv.cartridge.rom;

import com.nanobuntv.cartridge.item.CartridgeContents;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The game formats the consoles take (D11, D74, D136) and the BIOS files some of them need. NES and Famicom Disk
 * System games run on FCEUmm (D10; a disk game also needs {@code disksys.rom} in the core's system directory), SNES
 * games on Snes9x (D137), Genesis games on Genesis Plus GX (D151), Game Boy and Game Boy Color games on SameBoy (D139),
 * Game Boy Advance games on mGBA (D183), Nintendo 64 games on Mupen64Plus-Next (D189).
 */
public final class RomFormats {
    /** A BIOS file: stored once per server in the shared BIOS folder (D74) and checked against a fixed hash. */
    public record Bios(String system, String fileName, String sha1, int size) {}

    /** D75: the common 8 KB Disk System BIOS dump. */
    public static final Bios FDS_BIOS = new Bios(CartridgeContents.FDS, "disksys.rom",
            "57fe1bdee955bb48d357e463ccbf129496930b62", 8192);

    private static final byte[] FDS_DISK_MAGIC = "*NINTENDO-HVC*".getBytes(StandardCharsets.US_ASCII);

    private RomFormats() {}

    /**
     * The system a file is for, from its header: iNES / NES 2.0, an FDS image (fwNES header or raw disk), a Genesis
     * cartridge ({@link #isGenesis}), a Game Boy Advance cartridge ({@link #isGameBoyAdvance}), a Game Boy cartridge
     * ({@link #gameBoy}: which of the three kinds, D170), an SNES cartridge (its internal header, {@link #isSnes}) or a
     * Nintendo 64 cartridge in any byte order ({@link #n64Order}).
     */
    public static Optional<String> detect(byte[] data) {
        if (n64Order(data) != N64_NONE) return Optional.of(CartridgeContents.N64);
        if (data.length > 16 && data[0] == 'N' && data[1] == 'E' && data[2] == 'S' && data[3] == 0x1A) {
            return Optional.of(CartridgeContents.NES);
        }
        if (data.length > 16 && data[0] == 'F' && data[1] == 'D' && data[2] == 'S' && data[3] == 0x1A) {
            return Optional.of(CartridgeContents.FDS);
        }
        if (data.length > FDS_DISK_MAGIC.length && data[0] == 0x01
                && Arrays.equals(data, 1, 1 + FDS_DISK_MAGIC.length, FDS_DISK_MAGIC, 0, FDS_DISK_MAGIC.length)) {
            return Optional.of(CartridgeContents.FDS);
        }
        if (isGameBoyAdvance(data)) return Optional.of(CartridgeContents.GAME_BOY_ADVANCE);
        if (isGenesis(data)) return Optional.of(CartridgeContents.GENESIS); // before SNES: its check is looser
        Optional<String> gameBoy = gameBoy(data);
        if (gameBoy.isPresent()) return gameBoy;
        if (isSnes(data)) return Optional.of(CartridgeContents.SNES);
        return Optional.empty();
    }

    /** {@link #n64Order}: not an N64 ROM; big-endian (.z64, the cartridge's own); byte-swapped (.v64); little-endian (.n64). */
    static final int N64_NONE = 0, N64_BIG = 1, N64_SWAPPED = 2, N64_LITTLE = 3;

    /**
     * Whether this is a Nintendo 64 ROM, and in which byte order (D189): its first word is the PI bus setting 0x80371240,
     * as the cartridge stores it, with every pair of bytes swapped, or every word reversed. At least 1 MiB, in whole
     * words.
     */
    static int n64Order(byte[] data) {
        if (data.length < 0x100000 || data.length % 4 != 0) return N64_NONE;
        int b0 = data[0] & 0xFF, b1 = data[1] & 0xFF, b2 = data[2] & 0xFF, b3 = data[3] & 0xFF;
        if (b0 == 0x80 && b1 == 0x37 && b2 == 0x12 && b3 == 0x40) return N64_BIG;
        if (b0 == 0x37 && b1 == 0x80 && b2 == 0x40 && b3 == 0x12) return N64_SWAPPED;
        if (b0 == 0x40 && b1 == 0x12 && b2 == 0x37 && b3 == 0x80) return N64_LITTLE;
        return N64_NONE;
    }

    /**
     * A ROM as it's burned: a Nintendo 64 game in the cartridge's own big-endian order (.z64), whichever order the file
     * was in, so one game is one SHA-1 (D189); everything else as it is. May reorder {@code data} in place.
     */
    public static byte[] normalize(byte[] data) {
        int order = n64Order(data);
        if (order == N64_SWAPPED) {
            for (int i = 0; i + 1 < data.length; i += 2) {
                byte t = data[i];
                data[i] = data[i + 1];
                data[i + 1] = t;
            }
        } else if (order == N64_LITTLE) {
            for (int i = 0; i + 3 < data.length; i += 4) {
                byte t0 = data[i], t1 = data[i + 1];
                data[i] = data[i + 3];
                data[i + 1] = data[i + 2];
                data[i + 2] = t1;
                data[i + 3] = t0;
            }
        }
        return data;
    }

    private static final byte[] SEGA = "SEGA".getBytes(StandardCharsets.US_ASCII);

    /**
     * A Genesis / Mega Drive ROM: "SEGA" at the start of its header at 0x100 (a few games have it one byte later), or
     * the same in a Super Magic Drive dump (a 512-byte header, then 16 KB blocks with each block's odd bytes first and
     * its even bytes second; Genesis Plus GX undoes that itself).
     */
    static boolean isGenesis(byte[] data) {
        if (data.length >= 0x200) {
            for (int at : new int[] {0x100, 0x101}) {
                if (Arrays.equals(data, at, at + 4, SEGA, 0, 4)) return true;
            }
        }
        if (data.length >= 512 + 16384 && data.length % 16384 == 512 && (data[8] & 0xFF) == 0xAA && (data[9] & 0xFF) == 0xBB) {
            int odd = 512 + 0x80, even = 512 + 8192 + 0x80; // where 0x100..0x103 land in the first block
            return data[even] == 'S' && data[odd] == 'E' && data[even + 1] == 'G' && data[odd + 1] == 'A';
        }
        return false;
    }

    /**
     * A Game Boy Advance ROM (D183): an ARM branch at the start, the fixed 0x96 at 0xB2 and the header's complement
     * check (0xA0-0xBC) at 0xBD, which the real BIOS refuses to start without. The Nintendo logo isn't checked (the
     * tests' own ROM has none).
     */
    static boolean isGameBoyAdvance(byte[] data) {
        if (data.length < 0xC0 || (data[3] & 0xFF) != 0xEA || (data[0xB2] & 0xFF) != 0x96) return false;
        int sum = 0;
        for (int i = 0xA0; i <= 0xBC; i++) sum -= data[i] & 0xFF;
        return ((sum - 0x19) & 0xFF) == (data[0xBD] & 0xFF);
    }

    /** The cartridge types (header byte 0x147) Game Boy games use: none, MBC1-3, MBC5-7, the camera, HuC1/3, TAMA5. */
    private static final int[] GAME_BOY_CARTS = {0x00, 0x01, 0x02, 0x03, 0x05, 0x06, 0x08, 0x09, 0x0B, 0x0C, 0x0D, 0x0F,
            0x10, 0x11, 0x12, 0x13, 0x19, 0x1A, 0x1B, 0x1C, 0x1D, 0x1E, 0x20, 0x22, 0xFC, 0xFD, 0xFE, 0xFF};

    /**
     * A Game Boy ROM, and which kind (D170): its header (0x134-0x14C) adds up to the checksum at 0x14D (the boot ROM
     * refuses to start without it), with a known cartridge type and ROM size. Byte 0x143 says Color-only (0xC0) or
     * Color-enhanced (bit 7). The Nintendo logo isn't checked (the tests' own ROM has none).
     */
    static Optional<String> gameBoy(byte[] data) {
        if (data.length < 0x8000 || data.length % 0x4000 != 0) return Optional.empty();
        int sum = 0;
        for (int i = 0x134; i <= 0x14C; i++) sum = sum - (data[i] & 0xFF) - 1;
        if ((sum & 0xFF) != (data[0x14D] & 0xFF) || (data[0x148] & 0xFF) > 0x08) return Optional.empty();
        int type = data[0x147] & 0xFF;
        if (Arrays.stream(GAME_BOY_CARTS).noneMatch(t -> t == type)) return Optional.empty();
        int color = data[0x143] & 0xFF;
        if ((color & 0x80) == 0) return Optional.of(CartridgeContents.GAME_BOY);
        return Optional.of(color == 0xC0 ? CartridgeContents.GAME_BOY_COLOR : CartridgeContents.GAME_BOY_DUAL);
    }

    public static boolean isGameBoy(String system) {
        return CartridgeContents.GAME_BOY.equals(system) || CartridgeContents.GAME_BOY_DUAL.equals(system)
                || CartridgeContents.GAME_BOY_COLOR.equals(system);
    }

    /** SNES cartridges have no magic number; their header sits at one of these offsets (LoROM, HiROM, ExHiROM). */
    private static final int[] SNES_HEADERS = {0x7FC0, 0xFFC0, 0x40FFC0};

    /**
     * An SNES ROM, optionally with a 512-byte copier header: the internal header at one of its usual places has a
     * checksum and complement that add up, or (homebrew and hacks often leave the checksum unset) a known map mode and
     * a printable title.
     */
    static boolean isSnes(byte[] data) {
        int skip = data.length % 1024 == 512 ? 512 : 0;
        int size = data.length - skip;
        if (size < 0x8000 || size % 1024 != 0) return false;
        for (int header : SNES_HEADERS) {
            int at = skip + header;
            if (at + 0x40 > data.length) continue;
            int complement = u16(data, at + 0x1C), checksum = u16(data, at + 0x1E);
            if ((complement ^ checksum) == 0xFFFF && checksum != 0) return true;
            int mapMode = data[at + 0x15] & 0xFF;
            if ((mapMode & 0xE0) == 0x20 && (mapMode & 0x0F) <= 0x0A && printable(data, at, 21)) return true;
        }
        return false;
    }

    private static int u16(byte[] data, int at) {
        return (data[at] & 0xFF) | (data[at + 1] & 0xFF) << 8;
    }

    private static boolean printable(byte[] data, int at, int length) {
        boolean any = false;
        for (int i = 0; i < length; i++) {
            int c = data[at + i] & 0xFF;
            if (c < 0x20 || c > 0x7E) return false;
            any |= c != ' ';
        }
        return any;
    }

    public static boolean isKnown(String system) {
        return CartridgeContents.NES.equals(system) || CartridgeContents.FDS.equals(system)
                || CartridgeContents.SNES.equals(system) || CartridgeContents.GENESIS.equals(system) || isGameBoy(system)
                || CartridgeContents.GAME_BOY_ADVANCE.equals(system) || CartridgeContents.N64.equals(system);
    }

    /** A guess from the file name, for headerless files when header checks are off (D16). */
    public static String fromFileName(String fileName) {
        String name = fileName.toLowerCase(Locale.ROOT);
        if (name.endsWith(".fds")) return CartridgeContents.FDS;
        if (name.endsWith(".sfc") || name.endsWith(".smc")) return CartridgeContents.SNES;
        if (name.endsWith(".gb") || name.endsWith(".sgb")) return CartridgeContents.GAME_BOY;
        if (name.endsWith(".gbc") || name.endsWith(".cgb")) return CartridgeContents.GAME_BOY_COLOR;
        if (name.endsWith(".gba") || name.endsWith(".agb")) return CartridgeContents.GAME_BOY_ADVANCE;
        if (name.endsWith(".z64") || name.endsWith(".n64") || name.endsWith(".v64")) return CartridgeContents.N64;
        if (name.endsWith(".md") || name.endsWith(".gen") || name.endsWith(".smd") || name.endsWith(".bin")) {
            return CartridgeContents.GENESIS;
        }
        return CartridgeContents.NES;
    }

    /** The file types the Writer's file picker offers (D136). */
    public static final String[] FILE_PATTERNS = {"*.nes", "*.fds", "*.sfc", "*.smc", "*.md", "*.gen", "*.smd", "*.bin", "*.gb",
            "*.gbc", "*.sgb", "*.cgb", "*.gba", "*.agb", "*.z64", "*.n64", "*.v64"};

    private static final Pattern TAG = Pattern.compile("\\s*[(\\[][^)\\]]*[)\\]]");
    private static final Pattern TRAILING_ARTICLE = Pattern.compile("^(.+?), (The|A|An)( - .*)?$");

    /**
     * D116: a cartridge title from a game's file name: no extension, no No-Intro / GoodNES tags ("(USA)", "(Rev 1)",
     * "[!]"), and a trailing article moved to the front ("Legend of Zelda, The" becomes "The Legend of Zelda").
     */
    public static String titleFromFileName(String fileName) {
        int dot = fileName.lastIndexOf('.');
        String name = (dot > 0 ? fileName.substring(0, dot) : fileName).strip();
        String title = TAG.matcher(name).replaceAll("").replaceAll("\\s+", " ").strip();
        if (title.isEmpty()) return name; // nothing but tags: keep them
        Matcher m = TRAILING_ARTICLE.matcher(title);
        if (m.matches()) title = m.group(2) + " " + m.group(1) + (m.group(3) != null ? m.group(3) : "");
        return title;
    }

    /** Every BIOS file any system needs. */
    public static List<Bios> allBios() {
        return List.of(FDS_BIOS);
    }

    /** The BIOS a system needs, if any. */
    public static Optional<Bios> bios(String system) {
        return CartridgeContents.FDS.equals(system) ? Optional.of(FDS_BIOS) : Optional.empty();
    }

    /** The BIOS with this SHA-1, if it's one we know. */
    public static Optional<Bios> biosBySha1(String sha1) {
        return FDS_BIOS.sha1().equals(sha1) ? Optional.of(FDS_BIOS) : Optional.empty();
    }

    /** The name the core is given with the ROM; only the extension matters (it picks the loader). */
    public static String gameFileName(String system) {
        if (CartridgeContents.SNES.equals(system)) return "game.sfc";
        if (CartridgeContents.GENESIS.equals(system)) return "game.md";
        if (CartridgeContents.N64.equals(system)) return "game.z64";
        if (isGameBoy(system)) return CartridgeContents.GAME_BOY.equals(system) ? "game.gb" : "game.gbc";
        return "game." + system;
    }
}
