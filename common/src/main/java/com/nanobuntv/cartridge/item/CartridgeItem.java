package com.nanobuntv.cartridge.item;

import java.util.Map;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Predicate;
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

/** A burned cartridge (D15). Its name is the game title (D44: the item_name component, set when it's burned). */
public class CartridgeItem extends Item {
    /** Set by the client: the connected server doesn't have this game (D125). Tooltips are only built on clients. */
    public static Predicate<String> romMissing = sha1 -> false;

    /**
     * The cartridge's shape (its item model, {@code models/item/<shape>.json}) per system, other than the Pixelon's own
     * cart: a Super Pixelon cart for SNES games, a Vortex Drive cart for Genesis games, Pocketron carts (grey, black or
     * clear, D170), Pocketron Advance and Pixelon 64 carts (D143, D185, D189).
     */
    public static final Map<String, String> SHAPES = Map.of(
            CartridgeContents.SNES, "cartridge_16bit",
            CartridgeContents.GENESIS, "cartridge_vortex",
            CartridgeContents.GAME_BOY, "cartridge_pocketron",
            CartridgeContents.GAME_BOY_DUAL, "cartridge_pocketron_dual",
            CartridgeContents.GAME_BOY_COLOR, "cartridge_pocketron_color",
            CartridgeContents.GAME_BOY_ADVANCE, "cartridge_advance",
            CartridgeContents.N64, "cartridge_64");

    /** The shape of a cartridge stack: {@link #SHAPES}, or {@code cartridge}, the Pixelon's (blank carts too). */
    public static String shape(ItemStack stack) {
        CartridgeContents contents = CartridgeComponents.CARTRIDGE.get(stack);
        return contents == null ? "cartridge" : SHAPES.getOrDefault(contents.system(), "cartridge");
    }

    public CartridgeItem(Properties properties) {
        super(properties);
    }

    /** A burned cartridge holding {@code contents}, named after its game (D44). */
    public static ItemStack create(CartridgeContents contents) {
        return GameMedia.CARTRIDGE.create(contents);
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
        if (contents == null) {
            tooltip.accept(Component.translatable("cartridge_mod.cartridge.empty").withStyle(ChatFormatting.GRAY));
            ItemHelp.append(stack, tooltip);
            return;
        }
        tooltip.accept(Component.translatable("cartridge_mod.cartridge.system." + contents.system())
                .withStyle(ChatFormatting.GRAY));
        if (romMissing.test(contents.romSha1())) { // D125
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
