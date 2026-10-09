package com.nanobuntv.cartridge.block;

import com.nanobuntv.cartridge.Cartridge;
import com.nanobuntv.cartridge.platform.Services;
import java.util.function.Function;
import java.util.function.Supplier;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
//? if >=1.21.2 {
/*import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
*///?}
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;

/** Block registrations. Display names live in en_us.json; IDs stay brand-free (D51). */
public final class CartridgeBlocks {
    public static final Supplier<CrtTvBlock> CRT_TV = register("crt_tv", CrtTvBlock::new, MapColor.SAND);
    public static final Supplier<FlatscreenTvBlock> FLATSCREEN_TV = register("flatscreen_tv", FlatscreenTvBlock::new, MapColor.COLOR_BLACK);
    // D129: six spaces, so pistons leave it be.
    public static final Supplier<LargeFlatscreenTvBlock> LARGE_FLATSCREEN_TV = register("large_flatscreen_tv",
            p -> new LargeFlatscreenTvBlock(immovable(p)), MapColor.COLOR_BLACK);
    public static final Supplier<DisplayPanelBlock> DISPLAY_PANEL = register("display_panel", DisplayPanelBlock::new, MapColor.COLOR_BLACK);
    public static final Supplier<Console8BitBlock> CONSOLE_8BIT = register("console_8bit", Console8BitBlock::new, MapColor.COLOR_LIGHT_GRAY);
    public static final Supplier<Console16BitBlock> CONSOLE_16BIT = register("console_16bit", Console16BitBlock::new, MapColor.COLOR_LIGHT_GRAY); // D136
    public static final Supplier<ConsoleVortexBlock> CONSOLE_VORTEX = register("console_vortex", ConsoleVortexBlock::new, MapColor.COLOR_BLACK); // D151
    public static final Supplier<Console64Block> CONSOLE_64 = register("console_64", Console64Block::new, MapColor.COLOR_GRAY); // D189
    public static final Supplier<CartridgeWriterBlock> CARTRIDGE_WRITER = register("cartridge_writer", CartridgeWriterBlock::new, MapColor.COLOR_GRAY);

    private CartridgeBlocks() {}

    /** A block's properties in its map colour; from 1.21.2 they carry its id, which every block needs (an add-on's too). */
    public static BlockBehaviour.Properties properties(ResourceLocation id, MapColor color) {
        //? if >=1.21.2 {
        /*return BlockBehaviour.Properties.of().mapColor(color).setId(ResourceKey.create(Registries.BLOCK, id));
        *///?} else if >=1.20 {
        return BlockBehaviour.Properties.of().mapColor(color);
        //?} else {
        /*return BlockBehaviour.Properties.of(net.minecraft.world.level.material.Material.METAL, color);
        *///?}
    }

    /** Pistons can't move it. Before 1.20 the block itself says so ({@code getPistonPushReaction}). */
    private static BlockBehaviour.Properties immovable(BlockBehaviour.Properties properties) {
        //? if >=1.20 {
        return properties.pushReaction(PushReaction.BLOCK);
        //?} else {
        /*return properties;
        *///?}
    }

    public static void init() {}

    /** D52: machine-like, as tough as a hopper; drops only when mined with a pickaxe (mineable/pickaxe tag). */
    private static <T extends Block> Supplier<T> register(String name, Function<BlockBehaviour.Properties, T> factory, MapColor color) {
        return Services.REGISTRY.register(BuiltInRegistries.BLOCK, name, () -> factory.apply(properties(Cartridge.id(name), color)
                .strength(3.0F, 4.8F)
                .sound(SoundType.METAL)
                .requiresCorrectToolForDrops()
                .noOcclusion()));
    }
}
