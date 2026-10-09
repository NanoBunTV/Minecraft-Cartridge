package com.nanobuntv.cartridge.item;

import com.nanobuntv.cartridge.Cartridge;
import com.nanobuntv.cartridge.block.CartridgeBlocks;
import com.nanobuntv.cartridge.emu.ConsoleKind;
import com.nanobuntv.cartridge.platform.Services;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
//? if >=1.21.2 {
/*import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
*///?}
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;

/**
 * Item registrations, in creative-tab order. Display names live in en_us.json; IDs stay brand-free (D51). Every item
 * has how-to lines shown with Shift (D110, {@link ItemHelp}).
 */
public final class CartridgeItems {
    private static final List<Supplier<? extends Item>> ALL = new ArrayList<>();

    public static final Supplier<BlockItem> CRT_TV = block("crt_tv", CartridgeBlocks.CRT_TV);
    public static final Supplier<BlockItem> FLATSCREEN_TV = block("flatscreen_tv", CartridgeBlocks.FLATSCREEN_TV); // D129
    public static final Supplier<BlockItem> LARGE_FLATSCREEN_TV = block("large_flatscreen_tv", CartridgeBlocks.LARGE_FLATSCREEN_TV);
    public static final Supplier<BlockItem> DISPLAY_PANEL = block("display_panel", CartridgeBlocks.DISPLAY_PANEL); // D131
    public static final Supplier<BlockItem> CONSOLE_8BIT = block("console_8bit", CartridgeBlocks.CONSOLE_8BIT);
    public static final Supplier<BlockItem> CONSOLE_16BIT = block("console_16bit", CartridgeBlocks.CONSOLE_16BIT); // D136
    public static final Supplier<BlockItem> CONSOLE_VORTEX = block("console_vortex", CartridgeBlocks.CONSOLE_VORTEX); // D151
    public static final Supplier<BlockItem> CONSOLE_64 = block("console_64", CartridgeBlocks.CONSOLE_64); // D189
    public static final Supplier<BlockItem> CARTRIDGE_WRITER = block("cartridge_writer", CartridgeBlocks.CARTRIDGE_WRITER);
    public static final Supplier<Item> BLANK_CARTRIDGE = item("blank_cartridge", ItemHelp.HelpItem::new, 16); // D49
    public static final Supplier<CartridgeItem> CARTRIDGE = item("cartridge", CartridgeItem::new, 1); // D49: its own UUID (D24)
    public static final Supplier<Item> CONTROLLER_8BIT = item("controller_8bit", ItemHelp.HelpItem::new, 1);
    public static final Supplier<Item> LIGHT_GUN = item("light_gun", ItemHelp.HelpItem::new, 1);
    public static final Supplier<Item> CONTROLLER_16BIT = item("controller_16bit", ItemHelp.HelpItem::new, 1); // D141
    public static final Supplier<Item> MOUSE_16BIT = item("mouse_16bit", ItemHelp.HelpItem::new, 1); // D144
    public static final Supplier<Item> CONTROLLER_VORTEX = item("controller_vortex", ItemHelp.HelpItem::new, 1); // D152
    public static final Supplier<Item> CONTROLLER_64 = item("controller_64", ItemHelp.HelpItem::new, 1); // D189
    public static final Supplier<HandheldItem> POCKETRON = item("pocketron",
            p -> new HandheldItem(p, ConsoleKind.POCKETRON, HandheldItem.NOT_DYEABLE), 1); // D162
    public static final Supplier<HandheldItem> POCKETRON_COLOR = item("pocketron_color",
            p -> new HandheldItem(p, ConsoleKind.POCKETRON_COLOR, HandheldItem.DEFAULT_COLOR), 1);
    public static final Supplier<HandheldItem> POCKETRON_ADVANCE = item("pocketron_advance",
            p -> new HandheldItem(p, ConsoleKind.POCKETRON_ADVANCE, HandheldItem.ADVANCE_COLOR), 1); // D183
    public static final Supplier<HandheldItem> POCKETRON_ADVANCE_SP = item("pocketron_advance_sp",
            p -> new HandheldItem(p, ConsoleKind.POCKETRON_ADVANCE, HandheldItem.ADVANCE_SP_COLOR), 1);
    public static final Supplier<PocketronAdapterItem> POCKETRON_ADAPTER = item("pocketron_adapter", PocketronAdapterItem::new, 1); // D166
    public static final Supplier<Item> LINK_CABLE = item("link_cable", ItemHelp.HelpItem::new, 16); // D179
    public static final Supplier<AvCableItem> AV_CABLE = item("av_cable", AvCableItem::new, 16); // D53: one used per link
    public static final Supplier<MemoryCardItem> MEMORY_CARD = item("memory_card", MemoryCardItem::new, 1); // one savestate (D91)

    private CartridgeItems() {}

    public static void init() {}

    public static List<Supplier<? extends Item>> all() {
        return Collections.unmodifiableList(ALL);
    }

    /** An item's properties; from 1.21.2 they carry its id, which every item needs (an add-on's too). */
    public static Item.Properties properties(ResourceLocation id) {
        //? if >=1.21.2 {
        /*return new Item.Properties().setId(ResourceKey.create(Registries.ITEM, id));
        *///?} else {
        return new Item.Properties();
        //?}
    }

    /** A block item's properties: from 1.21.2 its name stays the block's ({@code block.<mod>.<id>}). */
    public static Item.Properties blockItemProperties(ResourceLocation id) {
        //? if >=1.21.2 {
        /*return properties(id).useBlockDescriptionPrefix();
        *///?} else {
        return properties(id);
        //?}
    }

    private static Supplier<BlockItem> block(String name, Supplier<? extends Block> block) {
        return add(Services.REGISTRY.register(BuiltInRegistries.ITEM, name,
                () -> new ItemHelp.HelpBlockItem(block.get(), blockItemProperties(Cartridge.id(name)))));
    }

    private static <T extends Item> Supplier<T> item(String name, Function<Item.Properties, T> factory, int maxStack) {
        return add(Services.REGISTRY.register(BuiltInRegistries.ITEM, name,
                () -> factory.apply(properties(Cartridge.id(name)).stacksTo(maxStack))));
    }

    private static <T extends Item> Supplier<T> add(Supplier<T> item) {
        ALL.add(item);
        return item;
    }
}
