package com.nanobuntv.cartridge.arcade.neoforge;

import com.nanobuntv.cartridge.arcade.CartridgeArcade;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;

@Mod(CartridgeArcade.MOD_ID)
public final class CartridgeArcadeNeoForge {
    public CartridgeArcadeNeoForge(IEventBus modBus, ModContainer container) {
        CartridgeArcade.init();
        com.nanobuntv.cartridge.neoforge.platform.NeoForgeRegistryHelper.attach(modBus); // the add-on's own registers
    }
}
