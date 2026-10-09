package com.nanobuntv.cartridge.neoforge;

import com.nanobuntv.cartridge.Cartridge;
import com.nanobuntv.cartridge.neoforge.platform.NeoForgeNetworkHelper;
import com.nanobuntv.cartridge.neoforge.platform.NeoForgeRegistryHelper;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;

@Mod(Cartridge.MOD_ID)
public final class CartridgeNeoForge {
    public CartridgeNeoForge(IEventBus modBus, ModContainer container) {
        Cartridge.init();
        NeoForgeRegistryHelper.attach(modBus);
        NeoForgeNetworkHelper.attach(modBus);
    }
}
