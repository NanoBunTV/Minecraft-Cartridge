package com.nanobuntv.cartridge.emu;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nanobuntv.cartridge.config.ClientSettings;
import com.nanobuntv.cartridge.config.ControlProfile;
import com.nanobuntv.cartridge.config.ControlSet;
import com.nanobuntv.cartridge.config.GamepadInput;
import com.nanobuntv.cartridge.item.CartridgeContents;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Console kinds and control sets an add-on registers (D195), as Cartridge Arcade does. */
class ConsoleKindTest {
    private static final CoreSpec CORE = new CoreSpec("test_core", "test 1", "1", Map.of("a", "1"), ConsoleKindTest.class);
    private static final ConsoleKind ADD_ON = ConsoleKind.builder("test_add_on", CORE)
            .systems(s -> s.startsWith("test/"))
            .gameFileName(s -> s.substring("test/".length()) + ".zip")
            .netplay(ConsoleKind.Netplay.BY_SPEED)
            .checksum(ConsoleKind.Checksum.PICTURE)
            .followPictureShape()
            .deflateStates()
            .option("b", "2")
            .register();
    private static final ControlSet ADD_ON_CONTROLS = ControlSet.register(new ControlSet("test_add_on", true,
            ControlSet.buttons(ControlSet.dpad(), List.of(ControlSet.joy("coin", "5", GamepadInput.BACK, 2)))), ADD_ON);

    @Test
    void cartridgesOwnKeepTheirWays() {
        assertSame(ConsoleKind.PIXELON_64, ConsoleKind.forSystem(CartridgeContents.N64).orElseThrow());
        assertTrue(ConsoleKind.PIXELON_64.lockstep() && ConsoleKind.PIXELON_64.deflatesStates());
        assertEquals(4, ConsoleKind.PIXELON_64.ports());
        assertFalse(ConsoleKind.SUPER_PIXELON.lockstep());
        assertEquals(ConsoleKind.Checksum.RAM, ConsoleKind.PIXELON.checksum());
        assertEquals("game.z64", ConsoleKind.PIXELON_64.gameFileName(CartridgeContents.N64));
        assertEquals("Game Boy Color", ConsoleKind.POCKETRON_COLOR.options().get("sameboy_model"));
        assertEquals(4.0 / 3.0, ConsoleKind.PIXELON.pictureAspect(256, 240), 1e-9);
        assertEquals(4.0 / 3.0, ConsoleKind.PIXELON.pictureAspect(160, 300), 1e-9, "only a kind that asks follows the shape");
        assertEquals(0, ConsoleKind.values().indexOf(ConsoleKind.PIXELON), "Cartridge's own come first");
    }

    @Test
    void anAddOnsKindFindsItsGames() {
        assertSame(ADD_ON, ConsoleKind.forSystem("test/sf2").orElseThrow());
        assertSame(ADD_ON, ConsoleKind.byId("test_add_on").orElseThrow());
        assertEquals("sf2.zip", ADD_ON.gameFileName("test/sf2"));
        assertFalse(ADD_ON.lockstep(), "by speed is decided by each client");
        assertEquals(ConsoleKind.Netplay.BY_SPEED, ADD_ON.netplay());
        assertEquals(Map.of("a", "1", "b", "2"), ADD_ON.options());
        assertEquals(3.0 / 4.0, ADD_ON.pictureAspect(224, 288), 1e-9, "an upright game");
        assertEquals(4.0 / 3.0, ADD_ON.pictureAspect(384, 224), 1e-9);
        assertSame(ConsoleKindTest.class, ADD_ON.core().jar());
        assertSame(CoreSpec.class, CoreSpec.FCEUMM.jar());
        assertThrows(IllegalStateException.class, () -> ConsoleKind.builder("test_add_on", CORE).register());
    }

    @Test
    void anAddOnsControlsAreKeptInTheClientConfig() {
        assertSame(ADD_ON_CONTROLS, ControlSet.of(ADD_ON));
        assertSame(ADD_ON_CONTROLS, ControlSet.byIndex(ADD_ON_CONTROLS.index()));
        assertSame(ControlSet.PIXELON, ControlSet.byIndex(-1));
        ClientSettings settings = new ClientSettings();
        ControlProfile profile = settings.controls(ADD_ON_CONTROLS);
        assertEquals("key.keyboard.5", profile.key("coin"));
        assertSame(profile, settings.addOns.get("test_add_on"));
        settings.addOns.put("gone_add_on", ControlSet.PIXELON.defaults()); // an add-on that's away: kept
        profile.keys.remove("up");
        settings.clean();
        assertEquals("key.keyboard.w", settings.controls(ADD_ON_CONTROLS).key("up"));
        assertTrue(settings.addOns.containsKey("gone_add_on"));
        assertThrows(IllegalStateException.class, () -> ControlSet.register(new ControlSet("other", true, List.of()), ADD_ON));
    }
}
