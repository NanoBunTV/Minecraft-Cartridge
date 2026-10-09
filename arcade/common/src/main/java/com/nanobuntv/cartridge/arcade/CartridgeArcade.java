package com.nanobuntv.cartridge.arcade;

import com.nanobuntv.cartridge.config.ControlSet;
import com.nanobuntv.cartridge.config.GamepadInput;
import com.nanobuntv.cartridge.emu.ConsoleKind;
import java.util.List;
import java.util.Locale;
import net.minecraft.resources.ResourceLocation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Cartridge Arcade (D195): MAME arcade cabinets for Cartridge, in a jar of its own. Common entry point, called by each
 * loader's mod initializer on both sides, after Cartridge's own.
 */
public final class CartridgeArcade {
    public static final String MOD_ID = "cartridge_arcade";
    public static final String MOD_NAME = "Cartridge: Arcade";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_NAME);
    /** An Arcade Board's system is this and its romset's name: {@code mame/sf2} (D197). */
    public static final String SYSTEM_PREFIX = "mame/";

    /**
     * The Arcade Cabinets (D194, D196, D198): MAME, two players, rollback or lockstep by each PC's speed, the
     * picture checksum, upright games at 3:4, savestates deflated (some are 14 MiB) wherever they leave the PC.
     * MAME finishes starting up on its first frame or two; a savestate loaded before that plays on differently, so
     * every client runs ten frames first.
     */
    public static final ConsoleKind ARCADE = ConsoleKind.builder("arcade", ArcadeCore.MAME)
            .systems(CartridgeArcade::isArcadeSystem)
            .gameFileName(system -> romset(system) + ".zip")
            .ports(2)
            .netplay(ConsoleKind.Netplay.BY_SPEED)
            .checksum(ConsoleKind.Checksum.PICTURE)
            .followPictureShape()
            .deflateStates()
            .bootFrames(10)
            .gameCheck(CartridgeArcade::looksLikeZip)
            .inputMap(ArcadeCore::mapInputs)
            .register();

    /**
     * The arcade controls (D199): a stick and six buttons, Coin, Start and P2 Start (D213). Keys in the fighting-game
     * layout, buttons 1 to 4 on the gamepad's face (bottom, right, left, top), 5 and 6 on the bumpers.
     */
    public static final ControlSet CONTROLS = ControlSet.register(new ControlSet("arcade", true, ControlSet.buttons(
            List.of(
                    ControlSet.joy("stick_up", "w", GamepadInput.DPAD_UP, 4),
                    ControlSet.joy("stick_down", "s", GamepadInput.DPAD_DOWN, 5),
                    ControlSet.joy("stick_left", "a", GamepadInput.DPAD_LEFT, 6),
                    ControlSet.joy("stick_right", "d", GamepadInput.DPAD_RIGHT, 7)),
            List.of(
                    ControlSet.joy("button_1", "j", GamepadInput.SOUTH, ArcadeCore.BUTTON_1),
                    ControlSet.joy("button_2", "k", GamepadInput.EAST, ArcadeCore.BUTTON_2),
                    ControlSet.joy("button_3", "l", GamepadInput.WEST, ArcadeCore.BUTTON_3),
                    ControlSet.joy("button_4", "u", GamepadInput.NORTH, ArcadeCore.BUTTON_4),
                    ControlSet.joy("button_5", "i", GamepadInput.LEFT_BUMPER, ArcadeCore.BUTTON_5),
                    ControlSet.joy("button_6", "o", GamepadInput.RIGHT_BUMPER, ArcadeCore.BUTTON_6),
                    ControlSet.joy("coin", "5", GamepadInput.BACK, ArcadeCore.COIN),
                    ControlSet.joy("start", "enter", GamepadInput.START, ArcadeCore.START),
                    ControlSet.joy("start_p2", "right.shift", GamepadInput.RIGHT_STICK, ArcadeCore.START_P2)))), ARCADE);

    private CartridgeArcade() {}

    public static void init() {
        LOGGER.info("{} loading: MAME {} for {}", MOD_NAME, ArcadeCore.MAME.marker(), ARCADE.id());
        ArcadeContent.init();
    }

    /**
     * The server's check of an uploaded board (D197): a zip, as far as the server can tell; whether MAME runs it was
     * checked on the burning player's PC (D206).
     */
    public static boolean looksLikeZip(byte[] data) {
        return data.length >= 22 && data[0] == 'P' && data[1] == 'K' && data[2] == 3 && data[3] == 4;
    }

    public static boolean isArcadeSystem(String system) {
        return system.startsWith(SYSTEM_PREFIX) && system.length() > SYSTEM_PREFIX.length();
    }

    /** The system for a romset: {@code mame/sf2}. MAME's set names are lower case. */
    public static String system(String romset) {
        return SYSTEM_PREFIX + romset.toLowerCase(Locale.ROOT);
    }

    /** The romset of an arcade system. */
    public static String romset(String system) {
        if (!isArcadeSystem(system)) throw new IllegalArgumentException("not an arcade system: " + system);
        return system.substring(SYSTEM_PREFIX.length());
    }

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }
}
