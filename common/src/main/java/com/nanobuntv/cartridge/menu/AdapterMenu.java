package com.nanobuntv.cartridge.menu;

import com.nanobuntv.cartridge.item.PocketronAdapterItem;
import java.util.List;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * The Pocketron Adapter's GUI (D166): one slot for a Game Boy cartridge, and the player's inventory, where the adapter
 * itself stays put while it's open. Color-only games don't fit (D167).
 */
public class AdapterMenu extends HeldItemMenu {
    public static final int SLOT_X = 26, SLOT_Y = 35, INVENTORY_Y = 122;

    private final Container container;

    /** Client side. */
    public AdapterMenu(int containerId, Inventory inventory) {
        this(containerId, inventory, new SimpleContainer(1), -1);
    }

    /** Server side: the adapter in this inventory slot. */
    public AdapterMenu(int containerId, Inventory inventory, int adapterSlot) {
        this(containerId, inventory, new HeldItemContainer(inventory.player, adapterSlot, 1, PocketronAdapterItem::isAdapter),
                adapterSlot);
    }

    private AdapterMenu(int containerId, Inventory inventory, Container container, int adapterSlot) {
        super(CartridgeMenus.POCKETRON_ADAPTER.get(), containerId, adapterSlot, 1);
        this.container = container;
        addSlot(RuleSlot.single(container, 0, SLOT_X, SLOT_Y, PocketronAdapterItem::fits));
        addInventory(inventory, INVENTORY_Y);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return quickMove(index, List.of(PocketronAdapterItem::fits));
    }

    @Override
    public boolean stillValid(Player player) {
        return container.stillValid(player);
    }
}
