package com.nanobuntv.cartridge.arcade;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.nanobuntv.cartridge.config.ControlProfile;
import com.nanobuntv.cartridge.config.ControlSet;
import com.nanobuntv.cartridge.config.GamepadInput;
import com.nanobuntv.cartridge.emu.ConsoleKind;
import com.nanobuntv.cartridge.emu.HostPlatform;
import com.nanobuntv.cartridge.emu.HostProcess;
import com.nanobuntv.cartridge.emu.Natives;
import com.nanobuntv.cartridge.emu.SharedMemory;
import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.zip.ZipInputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Cartridge Arcade's console and MAME (D194–D199). The real-game test runs MAME on the tester's own romsets from the
 * folder in {@code CARTRIDGE_ARCADE_ROMS} (Pac-Man, else Street Fighter II, else the first zip) and is skipped
 * without it.
 */
class ArcadeCoreTest {
    private static final Path ROOT = Path.of(System.getProperty("cartridge.root", "../../../.."));
    private static final int COIN = 1 << ArcadeCore.COIN, START = 1 << ArcadeCore.START;

    @TempDir
    Path temp;

    @Test
    void theArcadeIsAConsoleOfItsOwn() {
        assertSame(CartridgeArcade.ARCADE, ConsoleKind.forSystem("mame/sf2").orElseThrow());
        assertTrue(ConsoleKind.forSystem("mame/").isEmpty(), "a romset needs a name");
        assertTrue(ConsoleKind.forSystem("nes").orElseThrow() != CartridgeArcade.ARCADE);
        assertEquals("sf2.zip", CartridgeArcade.ARCADE.gameFileName(CartridgeArcade.system("SF2")));
        assertEquals(ConsoleKind.Netplay.BY_SPEED, CartridgeArcade.ARCADE.netplay());
        assertEquals(ConsoleKind.Checksum.PICTURE, CartridgeArcade.ARCADE.checksum());
        assertEquals(3.0 / 4.0, CartridgeArcade.ARCADE.pictureAspect(224, 288), 1e-9);
        assertEquals("disabled", CartridgeArcade.ARCADE.options().get("mame_buttons_profiles"));
        assertEquals("internal", CartridgeArcade.ARCADE.options().get("mame_rotation_mode"));
        assertTrue(CartridgeArcade.ARCADE.bootFrames() >= 2, "MAME starts up on its first frame");
    }

    @Test
    void theControlsAreTheFightingGameLayout() {
        assertSame(CartridgeArcade.CONTROLS, ControlSet.of(CartridgeArcade.ARCADE));
        ControlProfile keys = CartridgeArcade.CONTROLS.defaults();
        assertEquals("key.keyboard.j", keys.key("button_1"));
        assertEquals("key.keyboard.o", keys.key("button_6"));
        assertEquals("key.keyboard.5", keys.key("coin"));
        assertEquals(GamepadInput.SOUTH, keys.pad("button_1"));
        assertEquals(GamepadInput.NORTH, keys.pad("button_4"));
        assertEquals(GamepadInput.RIGHT_BUMPER, keys.pad("button_6"));
        Map<String, Integer> bits = new java.util.HashMap<>();
        for (ControlSet.Button b : CartridgeArcade.CONTROLS.buttons()) bits.put(b.name(), b.bits());
        assertEquals(1, bits.get("button_1"), "button 1 is RetroPad B");
        assertEquals(1 << 8, bits.get("button_2"), "button 2 is RetroPad A");
        assertEquals(COIN, bits.get("coin"));
        assertEquals("key.keyboard.right.shift", keys.key("start_p2"));
        assertEquals(GamepadInput.RIGHT_STICK, keys.pad("start_p2"));
    }

    /** P2 Start (D213): on either port it becomes port 2's Start, and its own bit never reaches MAME. */
    @Test
    void p2StartPressesTheSecondPlayersStart() {
        int p2Start = 1 << ArcadeCore.START_P2;
        int[] inputs = {p2Start | COIN, 0, 0, p2Start, START, 0}; // three frames, two ports
        CartridgeArcade.ARCADE.mapInputs(inputs, 3);
        assertEquals(COIN, inputs[0], "frame 1: port 1 keeps its other buttons");
        assertEquals(START, inputs[1], "frame 1: port 1's P2 Start is port 2's Start");
        assertEquals(0, inputs[2]);
        assertEquals(START, inputs[3], "frame 2: port 2's own P2 Start is its Start");
        assertEquals(START, inputs[4], "frame 3: port 1's own Start stays its own");
        assertEquals(0, inputs[5]);
    }

    @Test
    void bothLibrariesAreThePinnedMame() throws IOException {
        byte[] marker = ArcadeCore.MAME.marker().getBytes(StandardCharsets.US_ASCII);
        for (String file : new String[] {"mame_libretro.dll.zip", "mame_libretro.so.zip"}) {
            Path zip = ROOT.resolve("arcade/cores/mame").resolve(file);
            try (ZipInputStream in = new ZipInputStream(new BufferedInputStream(Files.newInputStream(zip)))) {
                assertTrue(in.getNextEntry() != null, file + " is empty");
                assertTrue(contains(in, marker), file + " is not " + ArcadeCore.MAME.version());
            }
        }
    }

    @Test
    void playsARealGame() throws Exception {
        String folder = System.getenv("CARTRIDGE_ARCADE_ROMS");
        assumeTrue(folder != null && Files.isDirectory(Path.of(folder)), "CARTRIDGE_ARCADE_ROMS not set");
        Path roms = Path.of(folder);
        Path game = null;
        for (String name : new String[] {"pacman.zip", "sf2.zip"}) {
            if (game == null && Files.exists(roms.resolve(name))) game = roms.resolve(name);
        }
        if (game == null) {
            try (var files = Files.list(roms)) {
                game = files.filter(f -> f.toString().endsWith(".zip")).findFirst().orElse(null);
            }
        }
        assumeTrue(game != null, "no romsets in " + roms);
        String set = game.getFileName().toString().replace(".zip", "");
        byte[] rom = Files.readAllBytes(game);
        HostPlatform platform = HostPlatform.current().orElseThrow();
        Path hostExe = ROOT.resolve("sidecar/zig-out/natives").resolve(platform.id).resolve(platform.hostFileName);
        Path core = Natives.extractCore(temp, platform, ArcadeCore.MAME);

        try (HostProcess a = start(hostExe, core); HostProcess b = start(hostExe, core)) {
            HostProcess.AvInfo av = load(a, set, rom);
            load(b, set, rom);
            assertTrue(av.fps() > 50 && av.fps() < 65, "fps " + av.fps());
            // Past the boot tests, then a coin and Start, as a player would.
            int frames = 1500;
            int[] in = new int[frames * 2];
            for (int f = 1260; f < 1270; f++) in[f * 2] = COIN;
            for (int f = 1320; f < 1330; f++) in[f * 2] = START;
            a.run(frames, HostProcess.RUN_MUTE | HostProcess.RUN_RENDER_LAST_ONLY, 2, in);
            long before = a.checksum();
            // Everyone starts from one savestate (a sync); from there the pictures agree frame for frame.
            byte[] state = a.serialize();
            b.unserialize(state);
            a.unserialize(state);
            int[] play = new int[300 * 2];
            for (int f = 0; f < 300; f++) play[f * 2] = f % 40 < 20 ? 1 << 7 | 1 : 1 << 6;
            a.run(300, HostProcess.RUN_MUTE, 2, play);
            b.run(300, HostProcess.RUN_MUTE | HostProcess.RUN_NO_VIDEO, 2, play);
            assertEquals(a.checksum(), b.checksum(), "the same picture after 300 frames");
            assertNotEquals(before, a.checksum(), "the picture moved on");
            SharedMemory.Frame frame = a.sharedMemory().readFrame(java.nio.ByteBuffer.allocateDirect(640 * 480 * 4));
            assertTrue(frame != null && frame.width() > 0, "a picture in shared memory");
        }
    }

    private HostProcess start(Path hostExe, Path core) throws IOException {
        assumeTrue(Files.exists(hostExe), "cartridge-host not built");
        HostProcess host = HostProcess.start(hostExe, temp.resolve("arcade-" + System.nanoTime() + SharedMemory.FILE_SUFFIX),
                temp.resolve("system"));
        assertEquals("MAME", host.loadCore(core).get("name"));
        return host;
    }

    private static HostProcess.AvInfo load(HostProcess host, String set, byte[] rom) throws IOException {
        for (var option : CartridgeArcade.ARCADE.options().entrySet()) host.setOption(option.getKey(), option.getValue());
        HostProcess.AvInfo av = host.loadGame(CartridgeArcade.ARCADE.gameFileName(CartridgeArcade.system(set)), rom);
        host.setChecksum(HostProcess.CHECKSUM_PICTURE);
        int boot = CartridgeArcade.ARCADE.bootFrames(); // as ConsoleInstance does
        host.run(boot, HostProcess.RUN_MUTE | HostProcess.RUN_NO_VIDEO, 2, new int[boot * 2]);
        return av;
    }

    private static boolean contains(InputStream in, byte[] marker) throws IOException {
        byte[] buf = new byte[1 << 20];
        byte[] carry = new byte[0];
        for (int n; (n = in.read(buf)) > 0; ) {
            byte[] window = new byte[carry.length + n];
            System.arraycopy(carry, 0, window, 0, carry.length);
            System.arraycopy(buf, 0, window, carry.length, n);
            outer:
            for (int i = 0; i <= window.length - marker.length; i++) {
                for (int j = 0; j < marker.length; j++) {
                    if (window[i + j] != marker[j]) continue outer;
                }
                return true;
            }
            carry = java.util.Arrays.copyOfRange(window, Math.max(0, window.length - marker.length + 1), window.length);
        }
        return false;
    }
}
