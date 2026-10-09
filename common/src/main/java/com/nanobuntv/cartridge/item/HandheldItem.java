package com.nanobuntv.cartridge.item;

import com.nanobuntv.cartridge.util.Use;
import com.nanobuntv.cartridge.CartridgeSounds;
import com.nanobuntv.cartridge.emu.ConsoleKind;
import com.nanobuntv.cartridge.menu.ConsoleMenu;
import com.nanobuntv.cartridge.menu.HandheldContainer;
import com.nanobuntv.cartridge.netplay.GameKey;
import com.nanobuntv.cartridge.netplay.NetSessions;
import com.nanobuntv.cartridge.util.Compat;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
//? if >=1.21.5 {
/*import net.minecraft.server.level.ServerLevel;
*///?}
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
//? if <1.21.2 {
import net.minecraft.world.InteractionResultHolder;
//?}
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
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
 * The Pocketron, the Pocketron Color (D158, D159, D162) and the Pocketron Advances (D183, D184): handhelds you hold
 * and play. Sneak + right-click opens its
 * GUI, with the cartridge and a Memory Card (D163) and the power switch; right-click switches it on and starts playing
 * (D164). Its game is a netplay game like a console's, keyed by the handheld's id (item/Handhelds).
 */
public class HandheldItem extends Item {
    public static final int CART_SLOT = 0, CARD_SLOT = 1, LINK_SLOT = 2, SLOTS = 3;
    /** The Pocketron Color's shell before it's dyed (D169): atomic purple. */
    public static final int DEFAULT_COLOR = 0x6B4FA8;
    /** The Pocketron Advance's (D185): indigo. */
    public static final int ADVANCE_COLOR = 0x4A3F9A;
    /** The Pocketron Advance SP's (D185): platinum. */
    public static final int ADVANCE_SP_COLOR = 0xB8BCC6;
    /** Not dyeable: the original Pocketron. */
    public static final int NOT_DYEABLE = -1;

    private final ConsoleKind kind;
    private final int defaultColor;

    /** {@code defaultColor}: the shell's colour before it's dyed, or {@link #NOT_DYEABLE}. */
    public HandheldItem(Properties properties, ConsoleKind kind, int defaultColor) {
        super(properties);
        this.kind = kind;
        this.defaultColor = defaultColor;
    }

    /** The item colour: a dyeable handheld's shell takes its dye (tint 0, D169, D185); everything else is as painted. */
    public static int tint(ItemStack stack, int tintIndex) {
        if (tintIndex != 0 || !(stack.getItem() instanceof HandheldItem item) || item.defaultColor == NOT_DYEABLE) return -1;
        return Compat.opaque(Compat.dye(stack, item.defaultColor));
    }

    public ConsoleKind kind() {
        return kind;
    }

    /** It takes a Link Cable (D176): the Pocketrons; the Advances don't link (D184). */
    public boolean linkable() {
        return kind == ConsoleKind.POCKETRON || kind == ConsoleKind.POCKETRON_COLOR;
    }

    public static boolean isLinkable(ItemStack stack) {
        return stack.getItem() instanceof HandheldItem item && item.linkable();
    }

    public static boolean isHandheld(ItemStack stack) {
        return stack.getItem() instanceof HandheldItem;
    }

    @Nullable
    public static HandheldState state(ItemStack stack) {
        return CartridgeComponents.HANDHELD.get(stack);
    }

    public static ItemStack slot(ItemStack handheld, int slot) {
        return CartridgeComponents.contents(handheld, SLOTS).get(slot);
    }

    static void setSlot(ItemStack handheld, int slot, ItemStack stack) {
        List<ItemStack> items = CartridgeComponents.contents(handheld, SLOTS);
        items.set(slot, stack);
        CartridgeComponents.setContents(handheld, items);
    }

    /** The game in a handheld, or null. */
    @Nullable
    public static CartridgeContents game(ItemStack handheld) {
        ItemStack cart = slot(handheld, CART_SLOT);
        return cart.is(CartridgeItems.CARTRIDGE.get()) ? CartridgeComponents.CARTRIDGE.get(cart) : null;
    }

    /** Only the dev self-test sets this: one player's two hands stand in for two linked players there. */
    public static boolean offhandAllowed;

    /**
     * Handhelds aren't held in the off hand (D187): one that gets there goes back into the inventory, or, with no room,
     * swaps back with the main hand (undoing the swap-hands key).
     */
    private static void leaveOffhand(ServerPlayer player, ItemStack stack) {
        Inventory inventory = player.getInventory();
        player.setItemSlot(EquipmentSlot.OFFHAND, ItemStack.EMPTY);
        int free = inventory.getFreeSlot();
        if (free >= 0) {
            inventory.setItem(free, stack);
        } else if (!isHandheld(player.getMainHandItem())) {
            player.setItemSlot(EquipmentSlot.OFFHAND, player.getMainHandItem());
            inventory.setItem(Compat.selectedSlot(inventory), stack);
        } else {
            Compat.drop(player, stack); // full, and a handheld in the main hand too
        }
        player.inventoryMenu.broadcastChanges(); // seen at once, even with another GUI open
        Compat.overlay(player, Component.translatable("cartridge_mod.handheld.no_offhand"));
    }

    /** A cartridge this handheld plays (D159). */
    public boolean takes(ItemStack cart) {
        CartridgeContents contents = cart.is(CartridgeItems.CARTRIDGE.get()) ? CartridgeComponents.CARTRIDGE.get(cart) : null;
        return contents != null && kind().takes(contents.system());
    }

    /**
     * Every handheld gets its own id the first tick it's in an inventory; a link pulled out while this one was away is
     * cleared when it turns up (D181).
     */
    //? if >=1.21.5 {
    /*@Override
    public void inventoryTick(ItemStack stack, ServerLevel level, Entity entity, @Nullable EquipmentSlot slot) {
        tick(stack, level, entity);
    }
    *///?} else {
    @Override
    public void inventoryTick(ItemStack stack, Level level, Entity entity, int slot, boolean selected) {
        tick(stack, level, entity);
    }
    //?}

    private void tick(ItemStack stack, Level level, Entity entity) {
        if (level.isClientSide) return;
        if (!CartridgeComponents.HANDHELD.has(stack)) {
            CartridgeComponents.HANDHELD.set(stack, new HandheldState(UUID.randomUUID(), false, 0));
        }
        if (entity instanceof ServerPlayer player && player.getOffhandItem() == stack && !offhandAllowed) {
            leaveOffhand(player, stack);
            return;
        }
        HandheldLink link = CartridgeComponents.HANDHELD_LINK.get(stack);
        if (link != null && entity instanceof ServerPlayer player && Handhelds.DEAD_LINKS.contains(link.linkId())) {
            Handhelds.clearLink(player, stack, link.linkId(), Component.translatable("cartridge_mod.link.unlinked"));
        }
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
        if (level.isClientSide || !(player instanceof ServerPlayer serverPlayer)) return Use.SUCCESS;
        HandheldState state = state(stack);
        if (state == null) {
            state = new HandheldState(UUID.randomUUID(), false, 0);
            CartridgeComponents.HANDHELD.set(stack, state);
        }
        if (player.isSecondaryUseActive()) {
            int inventorySlot = hand == InteractionHand.MAIN_HAND ? Compat.selectedSlot(player.getInventory()) : Inventory.SLOT_OFFHAND;
            HandheldContainer container = new HandheldContainer(serverPlayer, inventorySlot, this);
            player.openMenu(new SimpleMenuProvider((id, inventory, p) -> new ConsoleMenu(id, inventory, container,
                    container.menuData(), inventorySlot), stack.getHoverName()));
            return Use.CONSUME;
        }
        CartridgeContents game = game(stack);
        if (game == null) {
            Compat.overlay(player, Component.translatable("cartridge_mod.handheld.no_cartridge"));
            return Use.CONSUME;
        }
        if (!state.powered()) setPower(serverPlayer, stack, true);
        Handhelds.play(serverPlayer, hand, state.id());
        return Use.CONSUME;
    }

    /**
     * Switches a handheld on (a fresh power-on: a new boot id) or off; either way its saved moment is forgotten (D164).
     * The handheld must be in one of the player's hands.
     */
    public static void setPower(ServerPlayer player, ItemStack stack, boolean on) {
        HandheldState state = state(stack);
        if (state == null) return;
        HandheldLink link = CartridgeComponents.HANDHELD_LINK.get(stack);
        if (!on && link != null) { // switching one off pulls the cable out (D181)
            Handhelds.unlink(Compat.server(player), link, Component.translatable("cartridge_mod.link.unlinked"));
            state = state(stack);
        }
        int boot;
        do {
            boot = player.getRandom().nextInt();
        } while (boot == 0 || boot == state.bootId());
        CartridgeComponents.HANDHELD.set(stack, state.withPower(on, on ? boot : state.bootId()));
        NetSessions.forget(Compat.level(player), GameKey.handheld(state.id()));
        CartridgeSounds.play(player.level(), player.blockPosition(), on ? CartridgeSounds.POWER_ON : CartridgeSounds.POWER_OFF, 0.5F);
        changed(player, stack);
    }

    /** Tells netplay a handheld in this player's hands changed (power, cartridge). */
    public static void changed(ServerPlayer player, ItemStack stack) {
        HandheldState state = state(stack);
        if (state == null) return;
        for (InteractionHand hand : InteractionHand.values()) {
            if (player.getItemInHand(hand) == stack) NetSessions.changed(new Handhelds.Host(player, hand, state.id()));
        }
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
        tooltip.accept((game == null ? Component.translatable("cartridge_mod.handheld.empty")
                : Component.literal(game.title())).withStyle(ChatFormatting.GRAY));
        ItemHelp.append(stack, tooltip);
    }
}
