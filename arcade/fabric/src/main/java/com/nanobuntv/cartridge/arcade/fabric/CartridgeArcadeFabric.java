package com.nanobuntv.cartridge.arcade.fabric;

import com.nanobuntv.cartridge.arcade.CartridgeArcade;
import net.fabricmc.api.ModInitializer;

public final class CartridgeArcadeFabric implements ModInitializer {
    @Override
    public void onInitialize() {
        CartridgeArcade.init();
    }
}
