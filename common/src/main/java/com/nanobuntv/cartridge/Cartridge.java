package com.nanobuntv.cartridge;

import com.nanobuntv.cartridge.block.CartridgeBlocks;
import com.nanobuntv.cartridge.blockentity.CartridgeBlockEntities;
import com.nanobuntv.cartridge.config.CartridgeConfig;
import com.nanobuntv.cartridge.item.CartridgeComponents;
import com.nanobuntv.cartridge.item.CartridgeCreativeTab;
import com.nanobuntv.cartridge.item.CartridgeItems;
import com.nanobuntv.cartridge.item.Handhelds;
import com.nanobuntv.cartridge.menu.CartridgeMenus;
import com.nanobuntv.cartridge.net.CartridgeNetwork;
import com.nanobuntv.cartridge.netplay.NetSessions;
import com.nanobuntv.cartridge.platform.Services;
import com.nanobuntv.cartridge.rom.RomServer;
import com.nanobuntv.cartridge.save.SaveServer;
import net.minecraft.resources.ResourceLocation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Common entry point, called by each loader's mod initializer. */
public final class Cartridge {
    public static final String MOD_ID = "cartridge_mod"; // D2
    public static final String MOD_NAME = "Cartridge";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_NAME);

    private Cartridge() {}

    public static void init() {
        LOGGER.info("{} loading on {}{}", MOD_NAME, Services.PLATFORM.loaderName(),
                Services.PLATFORM.isDevelopmentEnvironment() ? " (development)" : "");
        CartridgeConfig.loadServer(); // D122
        // Blocks before items: block items look up their block when they are created.
        CartridgeComponents.init();
        CartridgeBlocks.init();
        CartridgeItems.init();
        CartridgeBlockEntities.init();
        CartridgeMenus.init();
        CartridgeSounds.init();
        CartridgeCreativeTab.init();
        CartridgeNetwork.init();
        Handhelds.init();
        // Leaving a singleplayer world: let go of its server (sessions, uploads, saves hold its levels and players).
        Services.PLATFORM.onServerStopped(() -> {
            NetSessions.reset();
            SaveServer.reset();
            RomServer.reset();
        });
    }

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }
}
