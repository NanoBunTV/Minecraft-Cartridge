package com.nanobuntv.cartridge.arcade.neoforge;

import com.nanobuntv.cartridge.arcade.CartridgeArcade;
import com.nanobuntv.cartridge.arcade.client.ArcadeClient;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;

@Mod(value = CartridgeArcade.MOD_ID, dist = Dist.CLIENT)
public final class CartridgeArcadeNeoForgeClient {
    public CartridgeArcadeNeoForgeClient(IEventBus modBus, ModContainer container) {
        ArcadeClient.init();
    }
}
