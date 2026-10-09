package com.nanobuntv.cartridge.menu;

import com.nanobuntv.cartridge.item.CartridgeComponents;
import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * The slots inside an item a player carries (the Pocketron Adapter's cartridge, D166; a handheld's cartridge and
 * Memory Card, D163), kept in the item's {@code minecraft:container} component. Server side; the menu locks the
 * inventory slot the item is in, so it stays put while its GUI is open.
 */
public class HeldItemContainer implements Container {
    protected final Player player;
    /** The player's inventory slot the item is in. */
    public final int inventorySlot;
    private final int size;
    private final Predicate<ItemStack> isItem;
    private List<ItemStack> items;
    private Object seen;

    public HeldItemContainer(Player player, int inventorySlot, int size, Predicate<ItemStack> isItem) {
        this.player = player;
        this.inventorySlot = inventorySlot;
        this.size = size;
        this.isItem = isItem;
    }

    /** The item holding the slots, or empty if it isn't where it was any more. */
    public ItemStack holder() {
        ItemStack stack = player.getInventory().getItem(inventorySlot);
        return isItem.test(stack) ? stack : ItemStack.EMPTY;
    }

    /** Reads the slots again when the item's component changed from elsewhere (a Memory Card save). */
    private List<ItemStack> items() {
        ItemStack holder = holder();
        Object now = CartridgeComponents.contentsState(holder);
        if (items == null || !Objects.equals(now, seen)) {
            items = CartridgeComponents.contents(holder, size);
            seen = now;
        }
        return items;
    }

    private void write() {
        ItemStack holder = holder();
        if (holder.isEmpty()) return;
        CartridgeComponents.setContents(holder, items());
        seen = CartridgeComponents.contentsState(holder);
        changed();
    }

    /** After every change to the slots. */
    protected void changed() {}

    @Override
    public int getContainerSize() {
        return size;
    }

    @Override
    public boolean isEmpty() {
        return items().stream().allMatch(ItemStack::isEmpty);
    }

    @Override
    public ItemStack getItem(int slot) {
        return items().get(slot);
    }

    @Override
    public ItemStack removeItem(int slot, int amount) {
        ItemStack stack = items().get(slot);
        if (stack.isEmpty() || amount <= 0) return ItemStack.EMPTY;
        ItemStack removed = stack.split(amount);
        write();
        return removed;
    }

    @Override
    public ItemStack removeItemNoUpdate(int slot) {
        ItemStack stack = items().get(slot);
        items.set(slot, ItemStack.EMPTY);
        write();
        return stack;
    }

    @Override
    public void setItem(int slot, ItemStack stack) {
        items().set(slot, stack);
        write();
    }

    @Override
    public int getMaxStackSize() {
        return 1;
    }

    @Override
    public void setChanged() {
        write();
    }

    @Override
    public boolean stillValid(Player player) {
        return player == this.player && !holder().isEmpty() && player.isAlive();
    }

    @Override
    public void clearContent() {
        items().replaceAll(s -> ItemStack.EMPTY);
        write();
    }
}
