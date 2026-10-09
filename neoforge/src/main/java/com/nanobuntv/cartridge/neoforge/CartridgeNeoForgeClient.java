package com.nanobuntv.cartridge.neoforge;

import com.nanobuntv.cartridge.Cartridge;
import com.nanobuntv.cartridge.client.CartridgeClient;
import com.nanobuntv.cartridge.client.CartridgeConfigScreen;
import com.nanobuntv.cartridge.neoforge.platform.NeoForgeClientEvents;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
//? if <1.21.4 {
import com.nanobuntv.cartridge.client.ItemIcons;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.neoforged.neoforge.client.event.ModelEvent;
//?}

@Mod(value = Cartridge.MOD_ID, dist = Dist.CLIENT)
public final class CartridgeNeoForgeClient {
    public CartridgeNeoForgeClient(IEventBus modBus, ModContainer container) {
        // The YACL config screen (D122), from the mod list's Config button.
        container.registerExtensionPoint(IConfigScreenFactory.class, (mod, parent) -> CartridgeConfigScreen.create(parent));

        //? if <1.21.4 {
        // Flat inventory icons for the 3D items (D43); from 1.21.4 the item model definitions choose them.
        modBus.addListener((ModelEvent.RegisterAdditional event) ->
                ItemIcons.models().forEach(id -> event.register(ModelResourceLocation.standalone(id))));
        ItemIcons.setLookup(id ->
                Minecraft.getInstance().getModelManager().getModel(ModelResourceLocation.standalone(id)));
        //?}

        NeoForgeClientEvents.attach(modBus);
        CartridgeClient.init();
    }
}
