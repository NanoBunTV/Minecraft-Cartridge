package com.nanobuntv.cartridge.fabric;

import com.nanobuntv.cartridge.Cartridge;
import net.fabricmc.api.ModInitializer;

public final class CartridgeFabric implements ModInitializer {
    @Override
    public void onInitialize() {
        Cartridge.init();
    }
}
