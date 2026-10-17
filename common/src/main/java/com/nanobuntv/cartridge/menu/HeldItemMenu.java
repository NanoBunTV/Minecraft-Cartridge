package com.nanobuntv.cartridge.menu;

import java.util.List;
import java.util.function.Predicate;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * A GUI for an item the player carries (the Pocketron Adapter, D166; a handheld, D163): its own slots first, then the
 * player's inventory, in which the item itself can't be picked up, swapped or moved while the GUI is open.
 */
public abstract class HeldItemMenu extends AbstractContainerMenu {
    /** The inventory slot the item is in (-1 on the client, which doesn't need to know). */
    private final int heldSlot;
    private final int ownSlots;

    protected HeldItemMenu(@Nullable MenuType<?> type, int containerId, int heldSlot, int ownSlots) {
        super(type, containerId);
        this.heldSlot = heldSlot;
        this.ownSlots = ownSlots;
    }

    /** Adds the player's inventory below the item's own slots, as every container GUI has it. */
    protected void addInventory(Inventory inventory, int y) {
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) addSlot(lockable(inventory, col + row * 9 + 9, 8 + col * 18, y + row * 18));
        }
        for (int col = 0; col < 9; col++) addSlot(lockable(inventory, col, 8 + col * 18, y + 58));
    }

    private Slot lockable(Inventory inventory, int index, int x, int y) {
        return new RuleSlot(inventory, index, x, y, stack -> true, 0).unlockedWhen(() -> index != heldSlot);
    }

    /** Number keys and the off-hand key swap slots without picking up: not with the held item. */
    @Override
    public void clicked(int slotId, int button, ClickType clickType, Player player) {
        if (clickType == ClickType.SWAP && heldSlot >= 0) {
            boolean onHeld = slotId >= 0 && slotId < slots.size() && slots.get(slotId).container instanceof Inventory
                    && slots.get(slotId).getContainerSlot() == heldSlot;
            if (button == heldSlot || onHeld) return;
        }
        super.clicked(slotId, button, clickType, player);
    }

    /** Shift-click: the item's slots to the inventory; from the inventory, into the first own slot that takes it. */
    protected ItemStack quickMove(int index, List<Predicate<ItemStack>> takes) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) return ItemStack.EMPTY;
        ItemStack stack = slot.getItem();
        ItemStack original = stack.copy();
        if (index < ownSlots) {
            if (!moveItemStackTo(stack, ownSlots, slots.size(), true)) return ItemStack.EMPTY;
        } else {
            boolean moved = false;
            for (int own = 0; own < ownSlots && !moved; own++) {
                if (takes.get(own).test(stack)) moved = moveItemStackTo(stack, own, own + 1, false);
            }
            if (!moved) return ItemStack.EMPTY;
        }
        if (stack.isEmpty()) slot.setByPlayer(ItemStack.EMPTY);
        else slot.setChanged();
        return original;
    }
}
