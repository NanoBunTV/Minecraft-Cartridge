package com.nanobuntv.cartridge.arcade;

import com.nanobuntv.cartridge.arcade.block.ArcadeCabinetBlock;
import com.nanobuntv.cartridge.arcade.block.ArcadeCabinetBlockEntity;
import com.nanobuntv.cartridge.arcade.item.ArcadeBoardItem;
import com.nanobuntv.cartridge.block.CartridgeBlocks;
import com.nanobuntv.cartridge.blockentity.CartridgeBlockEntities;
import com.nanobuntv.cartridge.item.CartridgeCreativeTab;
import com.nanobuntv.cartridge.item.CartridgeItems;
import com.nanobuntv.cartridge.item.GameMedia;
import com.nanobuntv.cartridge.item.ItemHelp;
import com.nanobuntv.cartridge.platform.Services;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.material.MapColor;
import org.jetbrains.annotations.Nullable;
//? if <1.20.5 {
/*import net.minecraft.world.level.Level;
*///?}
//? if >=1.21.5 {
/*import net.minecraft.world.item.component.TooltipDisplay;
*///?}

/**
 * Cartridge: Arcade's blocks and items (D195), registered through Cartridge's platform layer under
 * {@code cartridge_arcade}: the 1- and 2-player Arcade Cabinets (D198) and the Arcade Boards (D197, D200), with the
 * boards' media for the Cartridge Writer. They go in Cartridge's creative tab (D212).
 */
public final class ArcadeContent {
    private static final List<Supplier<? extends Item>> ITEMS = new java.util.ArrayList<>();

    public static final Supplier<ArcadeCabinetBlock> CABINET = block("arcade_cabinet", ArcadeCabinetBlock.ONE_PLAYER);
    public static final Supplier<ArcadeCabinetBlock> CABINET_2P = block("arcade_cabinet_2p", ArcadeCabinetBlock.TWO_PLAYERS);
    public static final Supplier<BlockEntityType<ArcadeCabinetBlockEntity>> CABINET_ENTITY = register(
            BuiltInRegistries.BLOCK_ENTITY_TYPE, "arcade_cabinet",
            () -> CartridgeBlockEntities.type(ArcadeCabinetBlockEntity::new, CABINET.get(), CABINET_2P.get()));

    public static final Supplier<Item> CABINET_ITEM = item("arcade_cabinet",
            () -> new CabinetItem(CABINET.get(), CartridgeItems.blockItemProperties(CartridgeArcade.id("arcade_cabinet"))));
    public static final Supplier<Item> CABINET_2P_ITEM = item("arcade_cabinet_2p",
            () -> new CabinetItem(CABINET_2P.get(), CartridgeItems.blockItemProperties(CartridgeArcade.id("arcade_cabinet_2p"))));
    public static final Supplier<Item> BLANK_BOARD = item("blank_arcade_board",
            () -> new ArcadeBoardItem(CartridgeItems.properties(CartridgeArcade.id("blank_arcade_board")).stacksTo(16), true));
    public static final Supplier<Item> BOARD = item("arcade_board",
            () -> new ArcadeBoardItem(CartridgeItems.properties(CartridgeArcade.id("arcade_board")).stacksTo(1), false));

    /** Arcade Boards for the Writer (D197): romset zips, systems {@code mame/<set>}. */
    public static final GameMedia BOARDS = GameMedia.register(new GameMedia("arcade_board", BLANK_BOARD, BOARD,
            CartridgeArcade::isArcadeSystem, new String[] {"*.zip"}));

    static { // in Cartridge's tab, after its items (D212); not the burned board: a dummy without a game
        CartridgeCreativeTab.addItems(output -> ITEMS.forEach(item -> {
            if (!GameMedia.isBurned(item.get())) output.accept(item.get());
        }));
    }

    static { // their "Hold Shift for details" lines (D110), cartridge_arcade.help.<item>.<n>
        ItemHelp.register(CartridgeArcade.id("arcade_cabinet"), 3);
        ItemHelp.register(CartridgeArcade.id("arcade_cabinet_2p"), 3);
        ItemHelp.register(CartridgeArcade.id("blank_arcade_board"), 1);
        ItemHelp.register(CartridgeArcade.id("arcade_board"), 2);
    }

    private ArcadeContent() {}

    public static void init() {}

    private static <R, T extends R> Supplier<T> register(Registry<R> registry, String name, Supplier<T> factory) {
        return Services.REGISTRY.register(registry, CartridgeArcade.id(name), factory);
    }

    private static Supplier<ArcadeCabinetBlock> block(String name, ArcadeCabinetBlock.Geometry geometry) {
        return register(BuiltInRegistries.BLOCK, name, () -> new ArcadeCabinetBlock(
                CartridgeBlocks.properties(CartridgeArcade.id(name), MapColor.COLOR_BLACK).strength(1.5F).sound(SoundType.WOOD)
                        .noOcclusion(), geometry));
    }

    private static Supplier<Item> item(String name, Supplier<? extends Item> factory) {
        Supplier<Item> item = register(BuiltInRegistries.ITEM, name, factory::get);
        ITEMS.add(item);
        return item;
    }

    /** A cabinet's item, dyeable in the crafting grid (D204: the vanilla dye recipe, through #minecraft:dyeable). */
    public static final class CabinetItem extends BlockItem {
        CabinetItem(Block block, Item.Properties properties) {
            super(block, properties.stacksTo(1));
        }

        //? if >=1.21.5 {
        /*@Override
        public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display,
                Consumer<Component> tooltip, TooltipFlag flag) {
            tooltip(stack, tooltip, flag);
        }
        *///?} else if >=1.20.5 {
        @Override
        public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
            tooltip(stack, tooltip::add, flag);
        }
        //?} else {
        /*@Override
        public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
            tooltip(stack, tooltip::add, flag);
        }
        *///?}

        private void tooltip(ItemStack stack, Consumer<Component> tooltip, TooltipFlag flag) {
            ItemHelp.append(stack, tooltip);
        }
    }
}
