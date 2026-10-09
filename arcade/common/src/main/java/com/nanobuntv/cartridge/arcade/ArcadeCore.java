package com.nanobuntv.cartridge.arcade;

import com.nanobuntv.cartridge.emu.CoreSpec;
import java.util.Map;

/**
 * MAME (D194), bundled in Cartridge Arcade's jar under {@code cores/<os>-<arch>/} with its license texts and source
 * link in {@code cores/mame/} (the repository's arcade/cores/mame). Its options are the same for every player:
 * <ul>
 *   <li>nothing read from or written to MAME's own config files, no command-line boot, no software lists, no cheats,
 *       no throttling (the host paces the frames);</li>
 *   <li>the joystick's dead zone, saturation and threshold fixed, so an analog input reads the same everywhere;</li>
 *   <li>vertical games turned upright inside MAME, so the picture arrives standing (D198);</li>
 *   <li>no per-game button layouts: buttons 1 to 6 are always RetroPad B, A, Y, X, L and R (D199).</li>
 * </ul>
 */
public final class ArcadeCore {
    public static final CoreSpec MAME = new CoreSpec("mame", "mame 0.289 9069f393", "0.289 (9069f393)", Map.ofEntries(
            Map.entry("mame_read_config", "disabled"),
            Map.entry("mame_write_config", "disabled"),
            Map.entry("mame_boot_from_cli", "disabled"),
            Map.entry("mame_softlists_enable", "disabled"),
            Map.entry("mame_cheats_enable", "disabled"),
            Map.entry("mame_throttle", "disabled"),
            Map.entry("mame_joystick_deadzone", "0.15"),
            Map.entry("mame_joystick_saturation", "0.95"),
            Map.entry("mame_joystick_threshold", "0.30"),
            Map.entry("mame_rotation_mode", "internal"),
            Map.entry("mame_buttons_profiles", "disabled")), ArcadeCore.class);

    /** RetroPad bits (RETRO_DEVICE_ID_JOYPAD_*) for MAME's buttons 1 to 6 without per-game layouts, Coin and Start. */
    public static final int BUTTON_1 = 0, BUTTON_2 = 8, BUTTON_3 = 1, BUTTON_4 = 9, BUTTON_5 = 10, BUTTON_6 = 11,
            COIN = 2, START = 3;
    /**
     * P2 Start on any player's controls (D213): R3's bit, which never reaches MAME. {@link #mapInputs} turns it into
     * the second port's Start, as a real panel has both start buttons (a two-player game on a one-player cabinet).
     */
    public static final int START_P2 = 15;

    /** {@link #START_P2} pressed on any port: off that port, Start on the second. */
    public static void mapInputs(int[] inputs, int offset, int ports) {
        boolean p2Start = false;
        for (int p = 0; p < ports; p++) {
            p2Start |= (inputs[offset + p] & 1 << START_P2) != 0;
            inputs[offset + p] &= ~(1 << START_P2);
        }
        if (p2Start && ports > 1) inputs[offset + 1] |= 1 << START;
    }

    private ArcadeCore() {}
}
