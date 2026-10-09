package com.nanobuntv.cartridge.item;

import com.nanobuntv.cartridge.util.Use;
import com.nanobuntv.cartridge.menu.AdapterMenu;
import com.nanobuntv.cartridge.util.Compat;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
//? if <1.21.2 {
import net.minecraft.world.InteractionResultHolder;
//?}
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;
//? if >=1.21.5 {
/*import net.minecraft.world.item.component.TooltipDisplay;
*///?}

/**
 * The Pocketron Adapter (D162, D166, D167), the Super Game Boy: a Game Boy cartridge goes into it through its own small
 * GUI (right-click it in hand), and the loaded adapter goes into the Super Pixelon like a cartridge. Color-only games
 * don't fit, as on the real one.
 */
public class PocketronAdapterItem extends Item {
    public PocketronAdapterItem(Properties properties) {
        super(properties);
    }

    public static boolean isAdapter(ItemStack stack) {
        return stack.is(CartridgeItems.POCKETRON_ADAPTER.get());
    }

    /** A game the adapter runs: Game Boy games, and those that also run on the Color (D167). */
    public static boolean fits(ItemStack stack) {
        CartridgeContents contents = stack.is(CartridgeItems.CARTRIDGE.get()) ? CartridgeComponents.CARTRIDGE.get(stack) : null;
        return contents != null && (CartridgeContents.GAME_BOY.equals(contents.system())
                || CartridgeContents.GAME_BOY_DUAL.equals(contents.system()));
    }

    /** The cartridge in an adapter, or empty. */
    public static ItemStack cartridge(ItemStack adapter) {
        return CartridgeComponents.contents(adapter, 1).get(0);
    }

    /**
     * The game a stack holds as a console slot sees it: a burned cartridge's (or board's), or the one in a Pocketron Adapter (D166);
     * null for none.
     */
    @Nullable
    public static CartridgeContents game(ItemStack stack) {
        if (isAdapter(stack)) stack = cartridge(stack);
        // A burned cartridge, or another burned game (an Arcade Board, D197).
        return GameMedia.ofBurned(stack).isPresent() ? CartridgeComponents.CARTRIDGE.get(stack) : null;
    }

    //? if >=1.21.2 {
    /*@Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        return onUse(level, player, hand).held(player.getItemInHand(hand), level);
    }
    *///?} else {
    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        return onUse(level, player, hand).held(player.getItemInHand(hand), level);
    }
    //?}

    private Use onUse(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!level.isClientSide) {
            int slot = hand == InteractionHand.MAIN_HAND ? Compat.selectedSlot(player.getInventory()) : Inventory.SLOT_OFFHAND;
            player.openMenu(new SimpleMenuProvider((id, inventory, p) -> new AdapterMenu(id, inventory, slot),
                    stack.getHoverName()));
        }
        return Use.SIDED;
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
        CartridgeContents game = game(stack);
        tooltip.accept((game == null ? Component.translatable("cartridge_mod.pocketron_adapter.empty")
                : Component.literal(game.title())).withStyle(ChatFormatting.GRAY));
        ItemHelp.append(stack, tooltip);
    }
}
