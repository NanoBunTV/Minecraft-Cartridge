package com.nanobuntv.cartridge.blockentity;

import com.nanobuntv.cartridge.block.CartridgeBlocks;
import com.nanobuntv.cartridge.platform.RegistryHelper;
import com.nanobuntv.cartridge.platform.Services;
import java.util.function.Supplier;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;

/** Block entity types. Registered after the blocks they belong to. */
public final class CartridgeBlockEntities {
    public static final Supplier<BlockEntityType<ConsoleBlockEntity>> CONSOLE_8BIT = Services.REGISTRY.register(
            BuiltInRegistries.BLOCK_ENTITY_TYPE, "console_8bit",
            () -> type(ConsoleBlockEntity::new, CartridgeBlocks.CONSOLE_8BIT.get(),
                    CartridgeBlocks.CONSOLE_16BIT.get(), CartridgeBlocks.CONSOLE_VORTEX.get(), CartridgeBlocks.CONSOLE_64.get())); // every console (D136)
    public static final Supplier<BlockEntityType<TvBlockEntity>> CRT_TV = Services.REGISTRY.register(
            BuiltInRegistries.BLOCK_ENTITY_TYPE, "crt_tv",
            () -> type(TvBlockEntity::new, CartridgeBlocks.CRT_TV.get(), CartridgeBlocks.FLATSCREEN_TV.get(),
                    CartridgeBlocks.LARGE_FLATSCREEN_TV.get(), CartridgeBlocks.DISPLAY_PANEL.get())); // every display (D129, D131)
    public static final Supplier<BlockEntityType<CartridgeWriterBlockEntity>> CARTRIDGE_WRITER = Services.REGISTRY.register(
            BuiltInRegistries.BLOCK_ENTITY_TYPE, "cartridge_writer",
            () -> type(CartridgeWriterBlockEntity::new, CartridgeBlocks.CARTRIDGE_WRITER.get()));

    private CartridgeBlockEntities() {}

    /** A block entity type for {@code blocks} (an add-on's too), as the loader makes one. */
    public static <T extends BlockEntity> BlockEntityType<T> type(RegistryHelper.BlockEntityFactory<T> factory, Block... blocks) {
        return Services.REGISTRY.blockEntityType(factory, blocks);
    }

    public static void init() {}
}
