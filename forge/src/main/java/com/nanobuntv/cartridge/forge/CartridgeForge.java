package com.nanobuntv.cartridge.forge;

import com.nanobuntv.cartridge.Cartridge;
import com.nanobuntv.cartridge.forge.platform.ForgeRegistryHelper;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLEnvironment;

/** Forge (1.19.4, 1.20.1; D210): one mod class for both sides, the client part only on a client. */
@Mod(Cartridge.MOD_ID)
public final class CartridgeForge {
    public CartridgeForge() {
        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();
        Cartridge.init();
        ForgeRegistryHelper.attach(modBus);
        if (FMLEnvironment.dist.isClient()) CartridgeForgeClient.init(modBus);
    }
}
