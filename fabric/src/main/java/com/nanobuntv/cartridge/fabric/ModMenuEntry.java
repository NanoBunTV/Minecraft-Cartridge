package com.nanobuntv.cartridge.fabric;

import com.nanobuntv.cartridge.client.CartridgeConfigScreen;
import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;

/** Mod Menu's Config button opens the YACL config screen (D122). Loaded only when Mod Menu is installed. */
public final class ModMenuEntry implements ModMenuApi {
    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return CartridgeConfigScreen::create;
    }
}
