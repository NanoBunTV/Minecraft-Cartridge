package com.nanobuntv.cartridge.item;

import com.nanobuntv.cartridge.Cartridge;
import com.nanobuntv.cartridge.platform.Services;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;

/**
 * D46: one "Cartridge" tab with everything, the console as its icon; not the burned Cartridge, a dummy without a game.
 * Add-ons put their items at its end (D212).
 */
public final class CartridgeCreativeTab {
    private static final List<Consumer<CreativeModeTab.Output>> ADD_ONS = new java.util.ArrayList<>();

    private CartridgeCreativeTab() {}

    public static void init() {
        Services.REGISTRY.creativeTab(Cartridge.id("main"), Component.translatable("itemGroup.cartridge_mod"),
                () -> new ItemStack(CartridgeItems.CONSOLE_8BIT.get()),
                output -> {
                    CartridgeItems.all().forEach(item -> {
                        if (!GameMedia.isBurned(item.get())) output.accept(item.get());
                    });
                    ADD_ONS.forEach(items -> items.accept(output));
                });
    }

    /** An add-on's items, after Cartridge's own; asked for each time the tab is filled. */
    public static void addItems(Consumer<CreativeModeTab.Output> items) {
        ADD_ONS.add(items);
    }
}
