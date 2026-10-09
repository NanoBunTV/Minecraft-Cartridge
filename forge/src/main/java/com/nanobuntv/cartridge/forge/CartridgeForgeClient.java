package com.nanobuntv.cartridge.forge;

import com.nanobuntv.cartridge.client.CartridgeClient;
import com.nanobuntv.cartridge.client.CartridgeConfigScreen;
import com.nanobuntv.cartridge.client.ItemIcons;
import com.nanobuntv.cartridge.forge.platform.ForgeClientEvents;
import net.minecraft.client.Minecraft;
import net.minecraftforge.client.ConfigScreenHandler;
import net.minecraftforge.client.event.ModelEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModLoadingContext;

/** The client half, from {@link CartridgeForge} on a client only. */
final class CartridgeForgeClient {
    private CartridgeForgeClient() {}

    static void init(IEventBus modBus) {
        // The YACL config screen (D122), from the mod list's Config button.
        ModLoadingContext.get().registerExtensionPoint(ConfigScreenHandler.ConfigScreenFactory.class,
                () -> new ConfigScreenHandler.ConfigScreenFactory((mc, parent) -> CartridgeConfigScreen.create(parent)));

        // Flat inventory icons for the 3D items (D43).
        modBus.addListener((ModelEvent.RegisterAdditional event) -> ItemIcons.models().forEach(event::register));
        ItemIcons.setLookup(id -> Minecraft.getInstance().getModelManager().getModel(id));

        ForgeClientEvents.attach(modBus);
        CartridgeClient.init();
    }
}
