package com.nanobuntv.cartridge.config;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * This player's own settings (D122): {@code config/cartridge_mod-client.json5}, on clients only. The controls are here
 * too, each console family's apart (D191), instead of in Minecraft's own Controls screen.
 */
public final class ClientSettings {
    @SerialEntry(comment = "While playing, hear the game at full volume wherever you stand instead of from the TV (D63).")
    public boolean playModeUnattenuatedAudio = false;

    @SerialEntry(comment = "The Pocketron's screen colours for Game Boy games (D165): LIME, GREYSCALE, OLIVE or TEAL.")
    public PocketronPalette pocketronPalette = PocketronPalette.LIME;

    @SerialEntry(comment = "Play with a gamepad (D118): the first one connected, read directly.")
    public boolean gamepadEnabled = true;
    @SerialEntry(comment = "Every console's: the key that toggles the big view (D69), as Minecraft names keys.")
    public String bigViewKey = "key.keyboard.f";
    @SerialEntry(comment = "Every console's: the gamepad input that toggles the big view (D146).")
    public GamepadInput padBigView = GamepadInput.LEFT_STICK;
    @SerialEntry(comment = "Every console's: the gamepad input that stops playing (D119); on the keyboard it's Esc.")
    public GamepadInput padStopPlaying = GamepadInput.RIGHT_STICK;

    // D191: each console family's buttons. Keys as Minecraft names them (key.keyboard.space, key.mouse.left); gamepad
    // inputs by position (SOUTH is A on Xbox, Cross on PlayStation), or a stick pushed one way (RIGHT_STICK_UP).
    @SerialEntry(comment = "The Pixelon Entertainment System and the Pixelon Blaster's trigger (fire).")
    public ControlProfile pixelon = ControlSet.PIXELON.defaults();
    @SerialEntry(comment = "The Super Pixelon, and the Pocketron Adapter in it.")
    public ControlProfile superPixelon = ControlSet.SUPER_PIXELON.defaults();
    @SerialEntry(comment = "The Vortex Drive.")
    public ControlProfile vortexDrive = ControlSet.VORTEX_DRIVE.defaults();
    @SerialEntry(comment = "The Pixelon 64. A gamepad's left stick is always its analog stick.")
    public ControlProfile pixelon64 = ControlSet.PIXELON_64.defaults();
    @SerialEntry(comment = "The Pocketron and the Pocketron Color.")
    public ControlProfile pocketron = ControlSet.POCKETRON.defaults();
    @SerialEntry(comment = "The Pocketron Advance and the Pocketron Advance SP.")
    public ControlProfile pocketronAdvance = ControlSet.POCKETRON_ADVANCE.defaults();
    @SerialEntry(comment = "Add-ons' consoles (D195), by their control set's id. Kept while an add-on is away.")
    public Map<String, ControlProfile> addOns = new LinkedHashMap<>();

    public ControlProfile controls(ControlSet set) {
        if (set == ControlSet.PIXELON) return pixelon;
        if (set == ControlSet.SUPER_PIXELON) return superPixelon;
        if (set == ControlSet.VORTEX_DRIVE) return vortexDrive;
        if (set == ControlSet.PIXELON_64) return pixelon64;
        if (set == ControlSet.POCKETRON) return pocketron;
        if (set == ControlSet.POCKETRON_ADVANCE) return pocketronAdvance;
        return addOns.computeIfAbsent(set.id(), id -> set.defaults());
    }


    /** A missing or unknown value in a hand-edited file reads as its default or as nothing mapped, not a crash. */
    public ClientSettings clean() {
        if (pocketronPalette == null) pocketronPalette = PocketronPalette.LIME;
        if (bigViewKey == null || bigViewKey.isBlank()) bigViewKey = ControlSet.NO_KEY;
        if (padBigView == null) padBigView = GamepadInput.NONE;
        if (padStopPlaying == null) padStopPlaying = GamepadInput.NONE;
        pixelon = clean(pixelon, ControlSet.PIXELON);
        superPixelon = clean(superPixelon, ControlSet.SUPER_PIXELON);
        vortexDrive = clean(vortexDrive, ControlSet.VORTEX_DRIVE);
        pixelon64 = clean(pixelon64, ControlSet.PIXELON_64);
        pocketron = clean(pocketron, ControlSet.POCKETRON);
        pocketronAdvance = clean(pocketronAdvance, ControlSet.POCKETRON_ADVANCE);
        addOns = addOns == null ? new LinkedHashMap<>() : new LinkedHashMap<>(addOns);
        addOns.values().removeIf(java.util.Objects::isNull);
        for (ControlSet set : ControlSet.values()) {
            if (addOns.containsKey(set.id())) addOns.put(set.id(), addOns.get(set.id()).fill(set));
        }
        return this;
    }

    private static ControlProfile clean(ControlProfile profile, ControlSet set) {
        return profile == null ? set.defaults() : profile.fill(set);
    }
}
