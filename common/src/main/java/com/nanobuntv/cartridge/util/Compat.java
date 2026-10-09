package com.nanobuntv.cartridge.util;

import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
//? if >=1.20.5 {
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.component.DyedItemColor;
//?} else {
/*import net.minecraft.nbt.CompoundTag;
*///?}
//? if >=1.21.11 {
/*import net.minecraft.server.permissions.Permission;
import net.minecraft.server.permissions.PermissionLevel;
*///?}
//? if >=1.21.2 {
/*import net.minecraft.util.ARGB;
*///?} else {
import net.minecraft.util.FastColor;
//?}

/**
 * Small differences between Minecraft versions, each in one place (Porting.md): the code calls these instead of the
 * version's own API. Client-only ones are in {@code client/ClientCompat}.
 */
public final class Compat {
    private Compat() {}

    /** The level a server player is in: getLevel() before 1.20, serverLevel() to 1.21.5, level() since. */
    public static ServerLevel level(ServerPlayer player) {
        //? if >=1.21.6 {
        /*return player.level();
        *///?} else if >=1.20 {
        return player.serverLevel();
        //?} else {
        /*return player.getLevel();
        *///?}
    }

    /** The server a player is on (the field went private in 1.21.6). */
    public static MinecraftServer server(ServerPlayer player) {
        return level(player).getServer();
    }

    /** The hotbar slot in use; a public field before 1.21.5, accessors after. */
    public static int selectedSlot(Inventory inventory) {
        //? if >=1.21.5 {
        /*return inventory.getSelectedSlot();
        *///?} else {
        return inventory.selected;
        //?}
    }

    public static void setSelectedSlot(Inventory inventory, int slot) {
        //? if >=1.21.5 {
        /*inventory.setSelectedSlot(slot);
        *///?} else {
        inventory.selected = slot;
        //?}
    }

    /** The 36 main-inventory and hotbar stacks; a public field before 1.21.5. */
    public static java.util.List<ItemStack> mainItems(Inventory inventory) {
        //? if >=1.21.5 {
        /*return inventory.getNonEquipmentItems();
        *///?} else {
        return inventory.items;
        //?}
    }

    /** Opaque {@code 0xAARRGGBB} from {@code 0xRRGGBB}. */
    public static int opaque(int rgb) {
        //? if >=1.21.2 {
        /*return ARGB.opaque(rgb);
        *///?} else if >=1.20.5 {
        return FastColor.ARGB32.opaque(rgb);
        //?} else {
        /*return 0xFF000000 | rgb;
        *///?}
    }

    /** Whether a player has a command permission level (0 everyone ... 4 owners); 1.21.11 made these permission sets. */
    public static boolean hasPermissionLevel(ServerPlayer player, int level) {
        //? if >=1.21.11 {
        /*return level <= 0 || player.permissions().hasPermission(new Permission.HasCommandLevel(PermissionLevel.byId(level)));
        *///?} else {
        return player.hasPermissions(level);
        //?}
    }

    /** An item's dye colour ({@code 0xRRGGBB}), or {@code fallback}: a data component from 1.20.5, NBT before. */
    public static int dye(ItemStack stack, int fallback) {
        //? if >=1.20.5 {
        return DyedItemColor.getOrDefault(stack, fallback);
        //?} else {
        /*CompoundTag display = stack.getTagElement("display");
        return display != null && display.contains("color", 99) ? display.getInt("color") : fallback;
        *///?}
    }

    public static void setDye(ItemStack stack, int rgb) {
        //? if >=1.21.5 {
        /*stack.set(DataComponents.DYED_COLOR, new DyedItemColor(rgb));
        *///?} else if >=1.20.5 {
        stack.set(DataComponents.DYED_COLOR, new DyedItemColor(rgb, true));
        //?} else {
        /*stack.getOrCreateTagElement("display").putInt("color", rgb);
        *///?}
    }

    /** A message over the hotbar (26.1 renamed displayClientMessage(message, true)). */
    /**
     * Names an item stack without the italics of a custom name (D44). Before 1.20.5 there is no such name: the
     * burned items' {@code getName} gives their game's title instead.
     */
    public static void setItemName(ItemStack stack, Component name) {
        //? if >=1.20.5 {
        stack.set(DataComponents.ITEM_NAME, name);
        //?}
    }

    /** Gives {@code to} the name an anvil gave {@code from}, if any. */
    public static void copyCustomName(ItemStack from, ItemStack to) {
        //? if >=1.20.5 {
        if (from.has(DataComponents.CUSTOM_NAME)) to.set(DataComponents.CUSTOM_NAME, from.get(DataComponents.CUSTOM_NAME));
        //?} else {
        /*if (from.hasCustomHoverName()) to.setHoverName(from.getHoverName());
        *///?}
    }

    /** One item off a stack, as a stack of its own (not taken in creative mode). */
    public static ItemStack takeOne(ItemStack stack, net.minecraft.world.entity.player.Player player) {
        //? if >=1.21 {
        return stack.consumeAndReturn(1, player);
        //?} else {
        /*ItemStack one = stack.copy();
        one.setCount(1);
        if (!player.getAbilities().instabuild) stack.shrink(1);
        return one;
        *///?}
    }

    /** Uses up items from a stack (not in creative mode). */
    public static void consume(ItemStack stack, int count, net.minecraft.world.entity.player.Player player) {
        //? if >=1.20.5 {
        stack.consume(count, player);
        //?} else {
        /*if (!player.getAbilities().instabuild) stack.shrink(count);
        *///?}
    }

    /** Drops a stack at the player's feet, as Minecraft does when an inventory is full. */
    public static void drop(net.minecraft.world.entity.player.Player player, ItemStack stack) {
        //? if >=26.3 {
        /*player.drop(stack, false, net.minecraft.util.Prediction.SERVER_ONLY);
        *///?} else {
        player.drop(stack, false);
        //?}
    }

    public static void overlay(net.minecraft.world.entity.player.Player player, Component message) {
        //? if >=26.1 {
        /*player.sendOverlayMessage(message);
        *///?} else {
        player.displayClientMessage(message, true);
        //?}
    }

    /** A chat click that runs {@code command} (1.21.5 made the events records per action). */
    public static ClickEvent runCommand(String command) {
        //? if >=1.21.5 {
        /*return new ClickEvent.RunCommand(command);
        *///?} else {
        return new ClickEvent(ClickEvent.Action.RUN_COMMAND, command);
        //?}
    }

    public static HoverEvent showText(Component text) {
        //? if >=1.21.5 {
        /*return new HoverEvent.ShowText(text);
        *///?} else {
        return new HoverEvent(HoverEvent.Action.SHOW_TEXT, text);
        //?}
    }
}
