package com.nanobuntv.cartridge.platform;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import java.util.function.Supplier;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.CreativeModeTab;
import java.util.function.Consumer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
//? if >=1.20 {
import net.minecraft.core.registries.BuiltInRegistries;
//?}

/**
 * Registers game objects under the {@code cartridge_mod} namespace, or an add-on's (D195). Fabric registers
 * immediately; NeoForge defers to its RegisterEvent, so call {@link Supplier#get()} only once registration has run,
 * never in static initializers. On NeoForge an add-on hands its own mod bus to NeoForgeRegistryHelper.attach after
 * declaring its entries.
 */
public interface RegistryHelper {
    default <R, T extends R> Supplier<T> register(Registry<R> registry, String name, Supplier<T> factory) {
        return register(registry, ResourceLocation.fromNamespaceAndPath("cartridge_mod", name), factory);
    }

    <R, T extends R> Supplier<T> register(Registry<R> registry, ResourceLocation id, Supplier<T> factory);

    /** A creative tab builder the loader can position (vanilla's own builder needs a fixed row and column). */
    CreativeModeTab.Builder creativeTabBuilder();

    /** A creative tab (D46). From 1.20 tabs are registry entries; before, each loader adds its own. */
    //? if >=1.20 {
    default void creativeTab(ResourceLocation id, Component title, Supplier<ItemStack> icon,
            Consumer<CreativeModeTab.Output> items) {
        register(BuiltInRegistries.CREATIVE_MODE_TAB, id, () -> creativeTabBuilder().title(title).icon(icon)
                .displayItems((parameters, output) -> items.accept(output)).build());
    }
    //?} else {
    /*void creativeTab(ResourceLocation id, Component title, Supplier<ItemStack> icon, Consumer<CreativeModeTab.Output> items);
    *///?}

    /** A menu type (its constructor isn't public in vanilla; both loaders open it up). */
    <T extends AbstractContainerMenu> Supplier<MenuType<T>> registerMenu(String name, MenuFactory<T> factory);

    @FunctionalInterface
    interface MenuFactory<T extends AbstractContainerMenu> {
        T create(int containerId, Inventory inventory);
    }

    /**
     * A block entity type for {@code blocks}. Vanilla's own factory interface and constructor (or builder, before
     * 1.21.2) aren't public; each loader has its way.
     */
    <T extends BlockEntity> BlockEntityType<T> blockEntityType(BlockEntityFactory<T> factory, Block... blocks);

    @FunctionalInterface
    interface BlockEntityFactory<T extends BlockEntity> {
        T create(BlockPos pos, BlockState state);
    }
}
