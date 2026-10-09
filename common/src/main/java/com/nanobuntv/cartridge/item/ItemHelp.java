package com.nanobuntv.cartridge.item;

import com.nanobuntv.cartridge.Cartridge;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.block.Block;
import org.jetbrains.annotations.Nullable;
//? if <1.20.5 {
/*import net.minecraft.world.level.Level;
*///?}
//? if >=1.21.5 {
/*import net.minecraft.world.item.component.TooltipDisplay;
*///?}

/**
 * How-to lines under the mod's items (D110): "Hold Shift for details", and with Shift held the lines
 * {@code <namespace>.help.<item>.1}, {@code .2}... from en_us.json. An add-on lists its own items with
 * {@link #register}.
 */
public final class ItemHelp {
    /** Set by the client to Screen::hasShiftDown; tooltips are only built on clients. */
    public static BooleanSupplier shiftDown = () -> false;

    /** How many help lines each item has in en_us.json. */
    private static final Map<String, Integer> LINES = new ConcurrentHashMap<>(Map.ofEntries(
            Map.entry("crt_tv", 2),
            Map.entry("flatscreen_tv", 2),
            Map.entry("large_flatscreen_tv", 2),
            Map.entry("display_panel", 3),
            Map.entry("console_8bit", 3),
            Map.entry("console_16bit", 3),
            Map.entry("controller_16bit", 2),
            Map.entry("mouse_16bit", 2),
            Map.entry("console_vortex", 3),
            Map.entry("controller_vortex", 2),
            Map.entry("console_64", 3),
            Map.entry("controller_64", 2),
            Map.entry("pocketron", 3),
            Map.entry("pocketron_color", 3),
            Map.entry("pocketron_advance", 3),
            Map.entry("pocketron_advance_sp", 3),
            Map.entry("pocketron_adapter", 2),
            Map.entry("link_cable", 2),
            Map.entry("cartridge_writer", 2),
            Map.entry("blank_cartridge", 1),
            Map.entry("cartridge", 2),
            Map.entry("controller_8bit", 3),
            Map.entry("light_gun", 2),
            Map.entry("av_cable", 2),
            Map.entry("memory_card", 2)));

    private ItemHelp() {}

    /** An add-on's item with {@code lines} help lines, {@code <namespace>.help.<path>.1}... in its en_us.json. */
    public static void register(ResourceLocation item, int lines) {
        LINES.put(item.toString(), lines);
    }

    public static void append(ItemStack stack, Consumer<Component> tooltip) {
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        boolean own = id.getNamespace().equals(Cartridge.MOD_ID);
        int lines = LINES.getOrDefault(own ? id.getPath() : id.toString(), 0);
        if (lines == 0) return;
        String item = id.getNamespace() + ".help." + id.getPath();
        if (!shiftDown.getAsBoolean()) {
            tooltip.accept(Component.translatable("cartridge_mod.help.hold_shift").withStyle(ChatFormatting.DARK_GRAY));
            return;
        }
        for (int i = 1; i <= lines; i++) {
            tooltip.accept(Component.translatable(item + "." + i).withStyle(ChatFormatting.GRAY));
        }
    }

    /** A plain item with help lines. */
    public static class HelpItem extends Item {
        public HelpItem(Properties properties) {
            super(properties);
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
            append(stack, tooltip);
        }
    }

    /** A block item with help lines. */
    public static class HelpBlockItem extends BlockItem {
        public HelpBlockItem(Block block, Properties properties) {
            super(block, properties);
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
            append(stack, tooltip);
        }
    }
}
