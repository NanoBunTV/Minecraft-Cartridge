package com.nanobuntv.cartridge.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.nanobuntv.cartridge.config.CartridgeConfig;
import com.nanobuntv.cartridge.config.ControlSet;
import net.minecraft.network.chat.Component;

/**
 * Play-mode keys (D22, D191): set per console family in Cartridge's own config ({@link ControlSet}), not in
 * Minecraft's Controls screen. They overlap the movement keys on purpose: in play mode the player can't move (D20) and
 * these keys go to the console instead. Keys are stored as Minecraft names them ({@code key.keyboard.space}).
 */
final class CartridgeKeys {
    private CartridgeKeys() {}

    /** The key a stored name stands for; {@link InputConstants#UNKNOWN} for none or a name that isn't a key. */
    static InputConstants.Key key(String name) {
        if (name == null) return InputConstants.UNKNOWN;
        try {
            return InputConstants.getKey(name);
        } catch (IllegalArgumentException e) {
            return InputConstants.UNKNOWN;
        }
    }

    /** The big view's key (D69), every console's. */
    static InputConstants.Key bigView() {
        return key(CartridgeConfig.client().bigViewKey);
    }

    static Component bigViewName() {
        return bigView().getDisplayName();
    }

    /** The Pixelon Blaster's trigger (D99), in the Pixelon Entertainment System's set. */
    static InputConstants.Key fire() {
        return key(CartridgeConfig.client().pixelon.key(ControlSet.FIRE));
    }
}
