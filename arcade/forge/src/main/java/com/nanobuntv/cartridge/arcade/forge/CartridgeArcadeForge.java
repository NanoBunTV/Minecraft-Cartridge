package com.nanobuntv.cartridge.arcade.forge;

import com.nanobuntv.cartridge.arcade.CartridgeArcade;
import com.nanobuntv.cartridge.arcade.client.ArcadeClient;
import com.nanobuntv.cartridge.forge.platform.ForgeRegistryHelper;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLEnvironment;

@Mod(CartridgeArcade.MOD_ID)
public final class CartridgeArcadeForge {
    public CartridgeArcadeForge() {
        CartridgeArcade.init();
        ForgeRegistryHelper.attach(FMLJavaModLoadingContext.get().getModEventBus()); // the add-on's own registers
        if (FMLEnvironment.dist.isClient()) ArcadeClient.init();
    }
}
