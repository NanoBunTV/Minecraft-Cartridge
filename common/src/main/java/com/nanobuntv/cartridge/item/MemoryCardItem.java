package com.nanobuntv.cartridge.item;

import java.util.List;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import org.jetbrains.annotations.Nullable;
//? if <1.20.5 {
/*import net.minecraft.world.level.Level;
*///?}
//? if >=1.21.5 {
/*import net.minecraft.world.item.component.TooltipDisplay;
*///?}

/** A Memory Card (D24, D91): one savestate, saved and loaded in the console's GUI (D92). */
public class MemoryCardItem extends Item {
    public MemoryCardItem(Properties properties) {
        super(properties);
    }

    public static boolean isCard(ItemStack stack) {
        return stack.is(CartridgeItems.MEMORY_CARD.get());
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
        MemoryCardContents contents = CartridgeComponents.MEMORY_CARD.get(stack);
        tooltip.accept((contents == null ? Component.translatable("cartridge_mod.memory_card.empty")
                : Component.literal(contents.title())).withStyle(ChatFormatting.GRAY)); // D98
        ItemHelp.append(stack, tooltip);
    }
}
