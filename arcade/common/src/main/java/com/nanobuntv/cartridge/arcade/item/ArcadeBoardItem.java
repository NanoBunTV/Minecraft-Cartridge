package com.nanobuntv.cartridge.arcade.item;

import com.nanobuntv.cartridge.arcade.CartridgeArcade;
import com.nanobuntv.cartridge.item.CartridgeComponents;
import com.nanobuntv.cartridge.item.CartridgeContents;
import com.nanobuntv.cartridge.item.CartridgeItem;
import com.nanobuntv.cartridge.item.ItemHelp;
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

/**
 * An Arcade Board (D197, D200): a burned one holds a MAME romset (its parent's and BIOS's files with it) and is named
 * after the game; a blank one is burned in the Cartridge Writer. Either slides into an Arcade Cabinet.
 */
public class ArcadeBoardItem extends Item {
    private final boolean blank;

    public ArcadeBoardItem(Properties properties, boolean blank) {
        super(properties);
        this.blank = blank;
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
        CartridgeContents contents = CartridgeComponents.CARTRIDGE.get(stack);
        if (blank || contents == null) {
            ItemHelp.append(stack, tooltip);
            return;
        }
        String set = CartridgeArcade.isArcadeSystem(contents.system()) ? CartridgeArcade.romset(contents.system()) : "?";
        tooltip.accept(Component.translatable("cartridge_arcade.board.romset", set).withStyle(ChatFormatting.GRAY));
        if (CartridgeItem.romMissing.test(contents.romSha1())) { // D125
            tooltip.accept(Component.translatable("cartridge_mod.cartridge.missing").withStyle(ChatFormatting.RED));
        }
        if (flag.isAdvanced()) {
            tooltip.accept(Component.literal("ROM " + contents.romSha1().substring(0, 12)).withStyle(ChatFormatting.DARK_GRAY));
        }
        ItemHelp.append(stack, tooltip);
    }

    //? if <1.20.5 {
    /*/^* Named after its game (D44); from 1.20.5 that's the stack's item name. ^/
    @Override
    public Component getName(ItemStack stack) {
        CartridgeContents contents = CartridgeComponents.CARTRIDGE.get(stack);
        return contents != null ? Component.literal(contents.title()) : super.getName(stack);
    }
    *///?}
}
