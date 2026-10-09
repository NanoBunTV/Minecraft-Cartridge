package com.nanobuntv.cartridge.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nanobuntv.cartridge.emu.ConsoleKind;
import com.nanobuntv.cartridge.emu.N64Pad;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** Each console family's controls (D191). */
class ControlSetTest {
    @Test
    void everyConsolePlaysWithASet() {
        for (ConsoleKind kind : ConsoleKind.values()) ControlSet.of(kind);
        assertEquals(ControlSet.SUPER_PIXELON, ControlSet.of(ConsoleKind.POCKETRON_ADAPTER), "the adapter plays on Super Pixelon Pads");
        assertEquals(ControlSet.POCKETRON, ControlSet.of(ConsoleKind.POCKETRON_COLOR));
    }

    @Test
    void namesAndKeysAreDistinctWithinASet() {
        for (ControlSet set : ControlSet.values()) {
            Set<String> names = new HashSet<>(), keys = new HashSet<>();
            for (ControlSet.Button b : set.buttons()) {
                assertTrue(names.add(b.name()), set + " " + b.name());
                assertTrue(keys.add(b.key()), set + " key " + b.key());
            }
            ClientSettings settings = new ClientSettings();
            assertEquals(set.buttons().size(), settings.controls(set).keys.size(), set + " defaults");
        }
    }

    @Test
    void theOldDefaultsCarryOver() {
        ControlProfile n64 = ControlSet.PIXELON_64.defaults();
        assertEquals("key.keyboard.left.control", n64.key("z"));
        assertEquals(GamepadInput.RIGHT_STICK_UP, n64.pad("c_up"));
        assertTrue(ControlSet.PIXELON_64.buttons().stream().anyMatch(b -> b.name().equals("c_up") && b.bits() == N64Pad.C_UP));
        assertEquals(GamepadInput.WEST, ControlSet.VORTEX_DRIVE.defaults().pad("a"), "the Vortex Pad's A is the left button (D152)");
        assertEquals("key.mouse.left", ControlSet.PIXELON.defaults().key(ControlSet.FIRE));
    }

    @Test
    void aHandEditedFileIsCleaned() {
        ClientSettings settings = new ClientSettings();
        settings.vortexDrive.keys.remove("c");          // left out: its default
        settings.vortexDrive.keys.put("a", null);        // broken: none
        settings.vortexDrive.pad.put("b", null);         // an input that doesn't exist reads as null
        settings.vortexDrive.keys.put("gone", "key.keyboard.g"); // a button that isn't there: dropped
        settings.pocketron = null;
        settings.clean();
        assertEquals("key.keyboard.space", settings.vortexDrive.key("c"));
        assertEquals(ControlSet.NO_KEY, settings.vortexDrive.key("a"));
        assertEquals(GamepadInput.NONE, settings.vortexDrive.pad("b"));
        assertTrue(!settings.vortexDrive.keys.containsKey("gone"));
        assertEquals(ControlSet.POCKETRON.defaults().keys, settings.pocketron.keys);
        assertTrue(!settings.pixelon64.pad.containsKey(ControlSet.STICK_UP), "the stick's rows have no gamepad input");
    }
}
