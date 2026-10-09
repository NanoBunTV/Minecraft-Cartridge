package com.nanobuntv.cartridge.fabric;

import com.nanobuntv.cartridge.client.CartridgeClient;
import com.nanobuntv.cartridge.fabric.platform.FabricNetworkHelper;
import net.fabricmc.api.ClientModInitializer;
//? if <1.21.4 {
import com.nanobuntv.cartridge.client.ItemIcons;
import net.fabricmc.fabric.api.client.model.loading.v1.ModelLoadingPlugin;
import net.minecraft.client.Minecraft;
//?}

public final class CartridgeFabricClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        //? if <1.21.4 {
        // Flat inventory icons for the 3D items (D43); from 1.21.4 the item model definitions choose them.
        ModelLoadingPlugin.register(context -> context.addModels(ItemIcons.models()));
        ItemIcons.setLookup(id -> Minecraft.getInstance().getModelManager().getModel(id));
        //?}

        FabricNetworkHelper.registerClientReceivers();
        CartridgeClient.init();
    }
}
