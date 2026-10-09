package com.nanobuntv.cartridge.fabric.platform;

import net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import com.nanobuntv.cartridge.platform.RegistryHelper;
import java.util.function.Supplier;
import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.CreativeModeTab;

/** Fabric registers straight into the vanilla registries, during mod initialization. */
public final class FabricRegistryHelper implements RegistryHelper {
    @Override
    public <R, T extends R> Supplier<T> register(Registry<R> registry, ResourceLocation id, Supplier<T> factory) {
        T value = Registry.register(registry, id, factory.get());
        return () -> value;
    }

    @Override
    public CreativeModeTab.Builder creativeTabBuilder() {
        //? if >=1.20 {
        return FabricItemGroup.builder();
        //?} else {
        /*return CreativeModeTab.builder(CreativeModeTab.Row.TOP, 0);
        *///?}
    }

    //? if <1.20 {
    /*@Override
    public void creativeTab(ResourceLocation id, net.minecraft.network.chat.Component title,
            Supplier<net.minecraft.world.item.ItemStack> icon, java.util.function.Consumer<CreativeModeTab.Output> items) {
        // Before 1.20 tabs aren't registry entries: Fabric's builder adds the tab when built.
        FabricItemGroup.builder(id).title(title).icon(icon).displayItems((parameters, output) -> items.accept(output)).build();
    }
    *///?}

    @Override
    public <T extends BlockEntity> BlockEntityType<T> blockEntityType(BlockEntityFactory<T> factory, Block... blocks) {
        return FabricBlockEntityTypeBuilder.create(factory::create, blocks).build();
    }

    @Override
    public <T extends AbstractContainerMenu> Supplier<MenuType<T>> registerMenu(String name, MenuFactory<T> factory) {
        return register(BuiltInRegistries.MENU, name, () -> new MenuType<>(factory::create, FeatureFlags.VANILLA_SET));
    }
}
