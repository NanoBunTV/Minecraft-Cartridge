package com.nanobuntv.cartridge.arcade.fabric;

import com.nanobuntv.cartridge.arcade.client.ArcadeClient;
import net.fabricmc.api.ClientModInitializer;

public final class CartridgeArcadeFabricClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        ArcadeClient.init();
    }
}
