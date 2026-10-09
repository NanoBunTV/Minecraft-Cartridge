package com.nanobuntv.cartridge.emu;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.nanobuntv.cartridge.item.CartridgeContents;
import com.nanobuntv.cartridge.rom.GameBoyAdvanceTestRom;
import com.nanobuntv.cartridge.rom.GameBoyTestRom;
import com.nanobuntv.cartridge.rom.GenesisTestRom;
import com.nanobuntv.cartridge.rom.RomFormats;
import com.nanobuntv.cartridge.rom.SnesTestRom;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Drives the real cartridge-host from Java, the way the mod will. Uses the host from natives/ and the core
 * and nestest ROM cached by sidecar/tools/harness.py (D59); skipped when those aren't present.
 */
class HostProcessTest {
    private static final Path ROOT = Path.of(System.getProperty("cartridge.root", "../../.."));
    private static final int START = 1 << 3;

    @TempDir
    Path temp;

    private HostProcess startHost() throws Exception {
        HostPlatform platform = HostPlatform.current().orElseThrow();
        Path host = ROOT.resolve("natives").resolve(platform.id).resolve(platform.hostFileName);
        assumeTrue(Files.exists(host), "cartridge-host not built");
        return HostProcess.start(host, temp.resolve("test-" + System.nanoTime() + SharedMemory.FILE_SUFFIX),
                temp.resolve("system"));
    }

    private static Path cached(String path) {
        Path p = ROOT.resolve("sidecar/.cache").resolve(path);
        assumeTrue(Files.exists(p), p + " not cached; run sidecar/tools/harness.py once");
        return p;
    }

    private static int[] inputs(int from, int frames) {
        int[] in = new int[frames * 2];
        for (int i = 0; i < frames; i++) {
            int frame = from + i;
            in[i * 2] = frame >= 60 && frame < 66 ? START : 0;
        }
        return in;
    }

    @Test
    void platformDetection() {
        assertEquals(HostPlatform.WINDOWS_X86_64, HostPlatform.detect("Windows 11", "amd64").orElseThrow());
        assertEquals(HostPlatform.LINUX_X86_64, HostPlatform.detect("Linux", "x86_64").orElseThrow());
        assertTrue(HostPlatform.detect("Mac OS X", "aarch64").isEmpty());
        assertTrue(HostPlatform.detect("Linux", "aarch64").isEmpty());
    }

    @Test
    void runsNestestDeterministically() throws Exception {
        HostPlatform platform = HostPlatform.current().orElseThrow();
        Path core = Natives.extractCore(temp, platform, CoreSpec.FCEUMM); // the bundled core (D114)
        byte[] rom = Files.readAllBytes(cached("roms/nestest.nes"));

        try (HostProcess a = startHost(); HostProcess b = startHost()) {
            assertEquals("FCEUmm", a.loadCore(core).get("name"));
            b.loadCore(core);
            HostProcess.AvInfo av = a.loadGame("nestest.nes", rom);
            b.loadGame("nestest.nes", rom);
            assertEquals(256, av.baseWidth());
            assertTrue(av.fps() > 59 && av.fps() < 61);

            assertEquals(300, a.run(300, 0, 2, inputs(0, 300)));
            byte[] state = a.serialize();
            a.run(300, 0, 2, inputs(300, 300));
            long sum = a.checksum();

            ByteBuffer pixels = ByteBuffer.allocateDirect(640 * 480 * 4);
            SharedMemory.Frame frame = a.sharedMemory().readFrame(pixels);
            assertNotNull(frame);
            assertEquals(256, frame.width());
            byte[] image = new byte[frame.width() * frame.height() * 4];
            pixels.get(0, image);

            // Audio: about 600 frames' worth at the core's rate.
            double expected = 600 / av.fps() * av.sampleRate();
            assertTrue(Math.abs(a.sharedMemory().audioPosition() - expected) / expected < 0.02);
            short[] audio = new short[2048];
            assertTrue(a.sharedMemory().readAudio(0, audio) > 0);

            // Savestate round trip, as rollback uses it (D12).
            a.unserialize(state);
            a.run(300, HostProcess.RUN_RENDER_LAST_ONLY | HostProcess.RUN_MUTE, 2, inputs(300, 300));
            assertEquals(sum, a.checksum());

            // A second process with the same inputs matches, frame and all. (Compared by checksum: a loaded state
            // may differ from a running one in core bookkeeping, see CHECKSUM in sidecar/src/main.c.)
            b.run(600, HostProcess.RUN_RENDER_LAST_ONLY, 2, inputs(0, 600));
            assertEquals(sum, b.checksum());
            ByteBuffer otherPixels = ByteBuffer.allocateDirect(640 * 480 * 4);
            assertNotNull(b.sharedMemory().readFrame(otherPixels));
            byte[] otherImage = new byte[image.length];
            otherPixels.get(0, otherImage);
            assertTrue(Arrays.equals(image, otherImage));

            // Rollback steps before the last one leave the picture alone (RUN_NO_VIDEO).
            long sequence = b.sharedMemory().frameSequence();
            b.run(5, HostProcess.RUN_NO_VIDEO | HostProcess.RUN_MUTE, 2, inputs(600, 5));
            assertEquals(sequence, b.sharedMemory().frameSequence());
            b.run(1, HostProcess.RUN_MUTE, 2, inputs(605, 1));
            assertTrue(b.sharedMemory().frameSequence() > sequence);

            assertThrows(HostProcess.HostException.class, () -> a.unserialize(new byte[3]));
        }
    }

    /**
     * A 16 KB NROM program with a battery: it counts up at $6000 forever. Bytes at $C000: SEI, CLD, LDA #0,
     * STA $2000, then INC $6000 / JMP back; NMI and IRQ point at an RTI.
     */
    private static byte[] batteryRom() {
        byte[] rom = new byte[16 + 16384 + 8192];
        byte[] header = {'N', 'E', 'S', 0x1A, 1, 1, 0x02, 0};
        System.arraycopy(header, 0, rom, 0, header.length);
        int[] code = {0x78, 0xD8, 0xA9, 0x00, 0x8D, 0x00, 0x20, 0xEE, 0x00, 0x60, 0x4C, 0x07, 0xC0, 0x40};
        for (int i = 0; i < code.length; i++) rom[16 + i] = (byte) code[i];
        int[] vectors = {0x0D, 0xC0, 0x00, 0xC0, 0x0D, 0xC0}; // NMI, RESET, IRQ
        for (int i = 0; i < vectors.length; i++) rom[16 + 0x3FFA + i] = (byte) vectors[i];
        return rom;
    }

    /**
     * A program that keeps copying $4017 (port 2: the Zapper's light sense in bit 3, trigger in bit 4) to battery RAM
     * at $6000, so the test can read what the game sees.
     */
    private static byte[] zapperRom() {
        byte[] rom = batteryRom();
        int[] code = {0x78, 0xD8, 0xA9, 0x00, 0x8D, 0x00, 0x20, 0xAD, 0x17, 0x40, 0x8D, 0x00, 0x60, 0x4C, 0x07, 0xC0, 0x40};
        for (int i = 0; i < code.length; i++) rom[16 + i] = (byte) code[i];
        int[] vectors = {0x10, 0xC0, 0x00, 0xC0, 0x10, 0xC0};
        for (int i = 0; i < vectors.length; i++) rom[16 + 0x3FFA + i] = (byte) vectors[i];
        return rom;
    }

    @Test
    void lightGunInPortTwo() throws Exception {
        HostPlatform platform = HostPlatform.current().orElseThrow();
        Path core = Natives.extractCore(temp, platform, CoreSpec.FCEUMM); // the bundled core (D114)
        try (HostProcess host = startHost()) {
            host.loadCore(core);
            host.setOption("fceumm_zapper_mode", "clightgun");
            host.loadGame("zapper.nes", zapperRom());
            host.setPortDevice(1, HostProcess.DEVICE_LIGHT_GUN);

            int aim = LightGun.encode(128, 120, false);
            host.run(5, 0, 2, new int[] {0, aim, 0, aim, 0, aim, 0, aim, 0, aim});
            int released = host.getSram()[0] & 0x18;
            int fire = LightGun.encode(128, 120, true);
            host.run(5, 0, 2, new int[] {0, fire, 0, fire, 0, fire, 0, fire, 0, fire});
            int pulled = host.getSram()[0] & 0x18;
            assertTrue(released != pulled, "the game sees the trigger: " + released + " vs " + pulled);
            assertEquals(0x08, pulled & 0x08, "a black screen: no light");

            host.setPortDevice(1, HostProcess.DEVICE_PAD); // and back to a pad
            assertThrows(HostProcess.HostException.class, () -> host.setPortDevice(0, 7));
        }
    }

    /** The Super Pixelon's core (D137) on a ROM written for it: red, then green once Start is pressed. */
    @Test
    void snes9xRunsAndTakesInput() throws Exception {
        HostPlatform platform = HostPlatform.current().orElseThrow();
        Path core = Natives.extractCore(temp, platform, CoreSpec.SNES9X);
        try (HostProcess host = startHost()) {
            host.loadCore(core);
            HostProcess.AvInfo av = host.loadGame("test.sfc", SnesTestRom.build());
            assertTrue(av.fps() > 59 && av.fps() < 61, "NTSC timing: " + av.fps());
            ByteBuffer frame = ByteBuffer.allocateDirect(640 * 480 * 4);
            host.run(10, 0, 2, new int[20]);
            int[] red = centre(host, frame);
            assertTrue(red[0] > 200 && red[1] < 60 && red[2] < 60, "red: " + Arrays.toString(red));

            byte[] state = host.serialize();
            int start = 1 << 3;
            host.run(10, 0, 2, new int[] {start, 0, start, 0, start, 0, start, 0, start, 0, start, 0, start, 0, start, 0, start, 0, start, 0});
            int[] green = centre(host, frame);
            assertTrue(green[1] > 200 && green[0] < 60, "green after Start: " + Arrays.toString(green));
            long after = host.checksum();

            // Rollback (D12): the same inputs from the same savestate give the same game.
            host.unserialize(state);
            host.run(10, 0, 2, new int[] {start, 0, start, 0, start, 0, start, 0, start, 0, start, 0, start, 0, start, 0, start, 0, start, 0});
            assertEquals(after, host.checksum());

            // The Super Pixelon Mouse (D144) plugs into either port.
            host.setPortDevice(1, HostProcess.DEVICE_MOUSE);
            int move = Mouse.encode(5, -3, true, false);
            host.run(2, 0, 2, new int[] {0, move, 0, move});
            host.setPortDevice(1, HostProcess.DEVICE_PAD);
        }
    }

    /**
     * The Vortex Drive's core (D151) on a ROM written for it: red, green once Start is pressed, blue once Z is (the
     * 6-button pad, D152: the Vortex Pad's Z is the RetroPad's R, the Super Pixelon Pad's R key).
     */
    @Test
    void genesisPlusGxRunsAndTakesInput() throws Exception {
        HostPlatform platform = HostPlatform.current().orElseThrow();
        Path core = Natives.extractCore(temp, platform, CoreSpec.GENESIS_PLUS_GX);
        try (HostProcess host = startHost()) {
            host.loadCore(core);
            for (var option : CoreSpec.GENESIS_PLUS_GX.options().entrySet()) host.setOption(option.getKey(), option.getValue());
            HostProcess.AvInfo av = host.loadGame("test.md", GenesisTestRom.build());
            assertTrue(av.fps() > 59 && av.fps() < 61, "NTSC timing: " + av.fps());
            ByteBuffer frame = ByteBuffer.allocateDirect(640 * 480 * 4);
            host.run(10, 0, 2, new int[20]);
            int[] red = centre(host, frame);
            assertTrue(red[0] > 200 && red[1] < 60 && red[2] < 60, "red: " + Arrays.toString(red));

            byte[] state = host.serialize();
            host.run(10, 0, 2, held(START, 10));
            int[] green = centre(host, frame);
            assertTrue(green[1] > 200 && green[0] < 60, "green after Start: " + Arrays.toString(green));
            long after = host.checksum();

            // Rollback (D12): the same inputs from the same savestate give the same game.
            host.unserialize(state);
            host.run(10, 0, 2, held(START, 10));
            assertEquals(after, host.checksum());

            host.run(10, 0, 2, held((1 << 11), 10));
            int[] blue = centre(host, frame);
            assertTrue(blue[2] > 200 && blue[0] < 60 && blue[1] < 60, "blue after Z: " + Arrays.toString(blue));
        }
    }

    /**
     * The Pocketrons' and the adapter's core (D139, D173) on a ROM written for it, as each model: the Color shows red,
     * then green after Start; the original and the Super Game Boy (with its border, D167) light, then dark. The audio
     * comes out near 48 kHz (the host averages SameBoy's 2 MHz down) and rollback gives the same game.
     */
    @Test
    void sameBoyRunsEachModel() throws Exception {
        HostPlatform platform = HostPlatform.current().orElseThrow();
        Path core = Natives.extractCore(temp, platform, CoreSpec.SAMEBOY);
        for (ConsoleKind kind : new ConsoleKind[] {ConsoleKind.POCKETRON_COLOR, ConsoleKind.POCKETRON, ConsoleKind.POCKETRON_ADAPTER}) {
            try (HostProcess host = startHost()) {
                host.loadCore(core);
                for (var option : kind.options().entrySet()) host.setOption(option.getKey(), option.getValue());
                HostProcess.AvInfo av = host.loadGame("test.gbc", GameBoyTestRom.build());
                // The Game Boy's 59.73 frames a second on all three: the adapter is a Super Game Boy 2 (D215), not the
                // first one, which runs 2.4% fast (61.2) off the SNES's clock.
                assertTrue(av.fps() > 59.6 && av.fps() < 59.9, kind + " timing: " + av.fps());
                assertTrue(av.sampleRate() > 40000 && av.sampleRate() < 50000, kind + " audio rate: " + av.sampleRate());
                ByteBuffer frame = ByteBuffer.allocateDirect(640 * 480 * 4);
                host.run(400, 0, 2, new int[800]); // past the boot ROM's logo
                SharedMemory.Frame f = host.sharedMemory().readFrame(frame);
                assertNotNull(f);
                if (kind == ConsoleKind.POCKETRON_ADAPTER) {
                    assertEquals(256, f.width(), "the Super Game Boy's border");
                    assertEquals(224, f.height());
                } else {
                    assertEquals(160, f.width());
                    assertEquals(144, f.height());
                }
                int[] before = centre(host, frame);
                byte[] state = host.serialize();
                host.run(10, 0, 2, held(START, 10));
                host.run(5, 0, 2, new int[10]);
                int[] after = centre(host, frame);
                long checksum = host.checksum();
                if (kind == ConsoleKind.POCKETRON_COLOR) {
                    assertTrue(before[0] > 150 && before[1] < 100, "red: " + Arrays.toString(before));
                    assertTrue(after[1] > 150 && after[0] < 100, "green after Start: " + Arrays.toString(after));
                } else {
                    assertTrue(sum(before) > sum(after) + 150, kind + " light, then dark: " + Arrays.toString(before)
                            + " " + Arrays.toString(after));
                }
                host.unserialize(state);
                host.run(10, 0, 2, held(START, 10));
                host.run(5, 0, 2, new int[10]);
                assertEquals(checksum, host.checksum(), kind + ": rollback gives the same game");
            }
        }
    }

    /**
     * The link cable (D177): SameBoy runs two Game Boys as one game, a Color on port 1 and an original on port 2,
     * side by side in one picture. Each port drives its own; savestates and rollback cover both.
     */
    @Test
    void sameBoyLinksTwoGameBoys() throws Exception {
        HostPlatform platform = HostPlatform.current().orElseThrow();
        Path core = Natives.extractCore(temp, platform, CoreSpec.SAMEBOY);
        try (HostProcess host = startHost()) {
            host.loadCore(core);
            for (var option : CoreSpec.SAMEBOY.options().entrySet()) host.setOption(option.getKey(), option.getValue());
            host.setOption("sameboy_link", "enabled");
            host.setOption("sameboy_screen_layout", "left-right");
            host.setOption("sameboy_model_1", "Game Boy Color");
            host.setOption("sameboy_model_2", "Game Boy");
            byte[] rom = GameBoyTestRom.build();
            HostProcess.AvInfo av = host.loadGameLinked(HostProcess.SUBSYSTEM_GAME_BOY_LINK,
                    new String[] {"one.gbc", "two.gbc"}, new byte[][] {rom, rom});
            assertTrue(av.fps() > 59 && av.fps() < 61, "timing: " + av.fps());
            ByteBuffer frame = ByteBuffer.allocateDirect(640 * 480 * 4);
            host.run(400, 0, 2, new int[800]);
            SharedMemory.Frame f = host.sharedMemory().readFrame(frame);
            assertNotNull(f);
            assertEquals(320, f.width(), "two screens side by side");
            assertEquals(144, f.height());
            int[] left = pixel(frame, f, 80, 72), right = pixel(frame, f, 240, 72);
            assertTrue(left[0] > 150 && left[1] < 100, "the Color starts red: " + Arrays.toString(left));
            int light = sum(right);

            byte[] state = host.serialize();
            int[] startOnTwo = new int[20];
            for (int i = 0; i < 10; i++) startOnTwo[i * 2 + 1] = START;
            host.run(10, 0, 2, startOnTwo);
            host.run(5, 0, 2, new int[10]);
            f = host.sharedMemory().readFrame(frame);
            left = pixel(frame, f, 80, 72);
            right = pixel(frame, f, 240, 72);
            assertTrue(left[0] > 150 && left[1] < 100, "port 2's Start leaves the Color red: " + Arrays.toString(left));
            assertTrue(sum(right) < light - 150, "and darkens the original: " + Arrays.toString(right));
            long checksum = host.checksum();

            host.unserialize(state);
            host.run(10, 0, 2, startOnTwo);
            host.run(5, 0, 2, new int[10]);
            assertEquals(checksum, host.checksum(), "rollback gives the same linked game");
            assertEquals(0, host.getMemory(HostProcess.MEMORY_GAME_BOY_1_SRAM).length, "the test ROM has no battery RAM");
        }
    }

    /**
     * Each linked Game Boy's battery save (D177) by its own memory id: put in before the first frame, still there after
     * frames run, and the two kept apart (a trade between two saves needs both).
     */
    @Test
    void linkedGameBoysHaveTheirOwnSaves() throws Exception {
        HostPlatform platform = HostPlatform.current().orElseThrow();
        Path core = Natives.extractCore(temp, platform, CoreSpec.SAMEBOY);
        try (HostProcess host = startHost()) {
            host.loadCore(core);
            for (var option : CoreSpec.SAMEBOY.options().entrySet()) host.setOption(option.getKey(), option.getValue());
            host.setOption("sameboy_link", "enabled");
            host.setOption("sameboy_screen_layout", "left-right");
            byte[] rom = GameBoyTestRom.withBattery();
            host.loadGameLinked(HostProcess.SUBSYSTEM_GAME_BOY_LINK, new String[] {"one.gb", "two.gb"}, new byte[][] {rom, rom});
            int one = HostProcess.MEMORY_GAME_BOY_1_SRAM, two = HostProcess.MEMORY_GAME_BOY_2_SRAM;
            assertEquals(8192, host.getMemory(one).length, "Game Boy 1's battery RAM");
            assertEquals(8192, host.getMemory(two).length, "Game Boy 2's battery RAM");

            byte[] first = new byte[8192], second = new byte[8192];
            first[100] = 0x11;
            second[100] = 0x22;
            host.setMemory(one, first);
            host.setMemory(two, second);
            host.run(60, 0, 2, new int[120]);
            assertEquals(0x11, host.getMemory(one)[100], "Game Boy 1 keeps its own save");
            assertEquals(0x22, host.getMemory(two)[100], "Game Boy 2 keeps its own save");
        }
    }

    /**
     * The Pocketron Advances' core (D183) on a ROM written for it: red, then green while Start is held, its battery
     * save written, its clock switched to emulated time (D186) and kept through savestates, and rollback gives the same
     * game.
     */
    @Test
    void mgbaRunsGameBoyAdvance() throws Exception {
        HostPlatform platform = HostPlatform.current().orElseThrow();
        Path core = Natives.extractCore(temp, platform, CoreSpec.MGBA);
        try (HostProcess host = startHost()) {
            host.loadCore(core);
            for (var option : ConsoleKind.POCKETRON_ADVANCE.options().entrySet()) host.setOption(option.getKey(), option.getValue());
            byte[] rom = GameBoyAdvanceTestRom.build();
            assertEquals(Optional.of(CartridgeContents.GAME_BOY_ADVANCE), RomFormats.detect(rom));
            HostProcess.AvInfo av = host.loadGame(RomFormats.gameFileName(CartridgeContents.GAME_BOY_ADVANCE), rom);
            assertTrue(av.fps() > 59 && av.fps() < 60, "timing: " + av.fps());
            assertTrue(av.sampleRate() > 30000 && av.sampleRate() < 70000, "audio rate (mGBA gives 65536 Hz): " + av.sampleRate());
            assertNotEquals(MgbaClock.read(host.serialize(), MgbaClock.GBA_STATE_SIZE)[0], 2L, "starts on the PC's clock");
            MgbaClock.useEmulatedTime(host);
            long[] clock = MgbaClock.read(host.serialize(), MgbaClock.GBA_STATE_SIZE);
            assertArrayEquals(new long[] {2, MgbaClock.START_MILLIS}, clock, "emulated time from a fixed start");

            ByteBuffer frame = ByteBuffer.allocateDirect(640 * 480 * 4);
            host.run(60, 0, 1, new int[60]);
            SharedMemory.Frame f = host.sharedMemory().readFrame(frame);
            assertNotNull(f);
            assertEquals(240, f.width());
            assertEquals(160, f.height());
            int[] red = centre(host, frame);
            assertTrue(red[0] > 150 && red[1] < 100, "red: " + Arrays.toString(red));
            assertEquals(0x5A, host.getSram()[0] & 0xFF, "the battery save it wrote");

            byte[] state = host.serialize();
            assertArrayEquals(clock, MgbaClock.read(state, MgbaClock.GBA_STATE_SIZE), "savestates keep emulated time");
            int[] start = new int[10];
            Arrays.fill(start, START);
            host.run(10, 0, 1, start);
            int[] green = centre(host, frame);
            assertTrue(green[1] > 150 && green[0] < 100, "green while Start is held: " + Arrays.toString(green));
            long checksum = host.checksum();
            host.unserialize(state);
            host.run(10, 0, 1, start);
            assertEquals(checksum, host.checksum(), "rollback gives the same game");
        }
    }

    /**
     * The Pixelon 64 (D189) on the real core, with a game you own: set CARTRIDGE_N64_ROM to its path (no N64 game can be
     * made for the tests without Nintendo's boot code). Four ports, the stick, deflated savestates and replays.
     */
    @Test
    void mupenRunsNintendo64() throws Exception {
        String path = System.getenv("CARTRIDGE_N64_ROM");
        assumeTrue(path != null && Files.exists(Path.of(path)), "CARTRIDGE_N64_ROM not set");
        HostPlatform platform = HostPlatform.current().orElseThrow();
        Path core = Natives.extractCore(temp, platform, CoreSpec.MUPEN64PLUS_NEXT);
        try (HostProcess host = startHost()) {
            String info = host.loadCore(core).toString();
            for (var option : ConsoleKind.PIXELON_64.options().entrySet()) host.setOption(option.getKey(), option.getValue());
            byte[] rom = RomFormats.normalize(Files.readAllBytes(Path.of(path)));
            assertEquals(Optional.of(CartridgeContents.N64), RomFormats.detect(rom));
            HostProcess.AvInfo av = host.loadGame(RomFormats.gameFileName(CartridgeContents.N64), rom);
            assertTrue(av.fps() > 49 && av.fps() < 61, "timing: " + av.fps());
            int ports = ConsoleKind.PIXELON_64.ports();
            int[] in = new int[600 * ports];
            for (int i = 0; i < 600; i++) {
                if (i % 60 < 4) in[i * ports] = N64Pad.START;
                in[i * ports + 3] = N64Pad.encode(N64Pad.A, 0.75F, -0.25F); // port 4 too
            }
            long t = System.nanoTime();
            host.run(600, 0, ports, in);
            double fps = 600 / ((System.nanoTime() - t) / 1e9);
            assertTrue(fps > 60, "faster than real time: " + fps + " fps (" + info + ")");
            ByteBuffer frame = ByteBuffer.allocateDirect(640 * 480 * 4);
            SharedMemory.Frame f = host.sharedMemory().readFrame(frame);
            assertNotNull(f);
            assertTrue(f.width() >= 320 && f.height() >= 200, f.width() + "x" + f.height());
            assertTrue(sum(centre(host, frame)) > 0 || sum(pixel(frame, f, f.width() / 3, f.height() / 3)) > 0, "a picture");

            byte[] state = States.deflate(host.serialize());
            assertTrue(state.length < 16 * 1024 * 1024, "deflated: " + state.length);
            int[] walk = new int[120 * ports];
            for (int i = 0; i < 120; i++) walk[i * ports] = N64Pad.encode(i % 20 < 3 ? N64Pad.A : 0, 1F, 0);
            host.run(120, 0, ports, walk);
            long checksum = host.checksum();
            host.unserialize(States.inflate(state));
            host.run(120, 0, ports, walk);
            assertEquals(checksum, host.checksum(), "the same inputs from the same savestate give the same game");
        }
    }

    private static int[] pixel(ByteBuffer frame, SharedMemory.Frame f, int x, int y) {
        int i = (y * f.width() + x) * 4;
        return new int[] {frame.get(i) & 0xFF, frame.get(i + 1) & 0xFF, frame.get(i + 2) & 0xFF};
    }

    private static int sum(int[] rgb) {
        return rgb[0] + rgb[1] + rgb[2];
    }

    private static int[] held(int buttons, int frames) {
        int[] in = new int[frames * 2];
        for (int i = 0; i < frames; i++) in[i * 2] = buttons;
        return in;
    }

    private static int[] centre(HostProcess host, ByteBuffer frame) {
        SharedMemory.Frame f = host.sharedMemory().readFrame(frame);
        assertNotNull(f);
        int i = (f.height() / 2 * f.width() + f.width() / 2) * 4;
        return new int[] {frame.get(i) & 0xFF, frame.get(i + 1) & 0xFF, frame.get(i + 2) & 0xFF};
    }

    @Test
    void batteryRamTravelsWithSavestates() throws Exception {
        HostPlatform platform = HostPlatform.current().orElseThrow();
        Path core = Natives.extractCore(temp, platform, CoreSpec.FCEUMM); // the bundled core (D114)
        try (HostProcess host = startHost()) {
            host.loadCore(core);
            host.loadGame("battery.nes", batteryRom());
            byte[] blank = host.getSram();
            assertEquals(8192, blank.length, "FCEUmm exposes NROM's work RAM as the battery save");

            // Battery saves (D24): written before the first frame, they're what the game sees.
            byte[] save = new byte[blank.length];
            save[100] = 0x5A;
            host.setSram(save);
            assertEquals(0x5A, host.getSram()[100]);
            assertThrows(HostProcess.HostException.class, () -> host.setSram(new byte[3]));

            host.run(10, 0, 2, new int[20]);
            byte[] before = host.getSram();
            assertEquals(0x5A, before[100]);
            byte[] state = host.serialize();
            host.run(7, 0, 2, new int[14]);
            assertTrue(!Arrays.equals(before, host.getSram()), "the game writes its battery RAM");

            // A savestate carries the battery RAM, so loading one (rollback, a Memory Card) brings it back.
            host.unserialize(state);
            assertTrue(Arrays.equals(before, host.getSram()));
        }
    }
}
