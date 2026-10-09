package com.nanobuntv.cartridge.menu;

import com.nanobuntv.cartridge.blockentity.CartridgeWriterBlockEntity;
import com.nanobuntv.cartridge.rom.RomServer;
import com.nanobuntv.cartridge.save.SaveServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * The Cartridge Writer's GUI (D15, D81): the main slot, the source slot and the player's inventory. Copy and Erase
 * are menu buttons ({@link #BUTTON_COPY}, {@link #BUTTON_ERASE}); burning a file goes through the upload payloads
 * (rom/RomServer), which find the Writer through the player's open menu.
 */
public class WriterMenu extends AbstractContainerMenu {
    public static final int BUTTON_COPY = 0, BUTTON_ERASE = 1;
    public static final int MAIN_X = 44, MAIN_Y = 38, SOURCE_X = 12, SOURCE_Y = 38;
    public static final int INVENTORY_Y = 104;

    private final Container container;

    /** Client side. */
    public WriterMenu(int containerId, Inventory inventory) {
        this(containerId, inventory, new SimpleContainer(2));
    }

    public WriterMenu(int containerId, Inventory inventory, Container container) {
        super(CartridgeMenus.CARTRIDGE_WRITER.get(), containerId);
        checkContainerSize(container, 2);
        this.container = container;
        container.startOpen(inventory.player);
        addSlot(new WriterSlot(container, CartridgeWriterBlockEntity.MAIN, MAIN_X, MAIN_Y));
        addSlot(new WriterSlot(container, CartridgeWriterBlockEntity.SOURCE, SOURCE_X, SOURCE_Y));
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(inventory, col + row * 9 + 9, 8 + col * 18, INVENTORY_Y + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) addSlot(new Slot(inventory, col, 8 + col * 18, INVENTORY_Y + 58));
    }

    /** The Writer this menu belongs to; null on the client. */
    @Nullable
    public CartridgeWriterBlockEntity writer() {
        return container instanceof CartridgeWriterBlockEntity writer ? writer : null;
    }

    public ItemStack main() {
        return slots.get(CartridgeWriterBlockEntity.MAIN).getItem();
    }

    public ItemStack source() {
        return slots.get(CartridgeWriterBlockEntity.SOURCE).getItem();
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        CartridgeWriterBlockEntity writer = writer();
        if (writer == null || writer.busy()) return false;
        return switch (id) {
            case BUTTON_COPY -> writer.copy();
            case BUTTON_ERASE -> writer.erase();
            default -> false;
        };
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) return ItemStack.EMPTY;
        ItemStack stack = slot.getItem();
        ItemStack original = stack.copy();
        if (index < 2) {
            if (!moveItemStackTo(stack, 2, slots.size(), true)) return ItemStack.EMPTY;
        } else if (CartridgeWriterBlockEntity.isBurned(stack)) {
            // A burned cart goes to the source slot (to copy it), or to the main slot (to erase it) if that's taken.
            if (!moveItemStackTo(stack, CartridgeWriterBlockEntity.SOURCE, CartridgeWriterBlockEntity.SOURCE + 1, false)
                    && !moveItemStackTo(stack, CartridgeWriterBlockEntity.MAIN, CartridgeWriterBlockEntity.MAIN + 1, false)) {
                return ItemStack.EMPTY;
            }
        } else if (!moveItemStackTo(stack, CartridgeWriterBlockEntity.MAIN, CartridgeWriterBlockEntity.MAIN + 1, false)) {
            return ItemStack.EMPTY;
        }
        if (stack.isEmpty()) slot.setByPlayer(ItemStack.EMPTY);
        else slot.setChanged();
        return original;
    }

    @Override
    public boolean stillValid(Player player) {
        return container.stillValid(player);
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        container.stopOpen(player);
        if (player instanceof ServerPlayer serverPlayer) {
            RomServer.cancelUpload(serverPlayer);
            SaveServer.cancel(serverPlayer);
        }
    }

    private static final class WriterSlot extends Slot {
        WriterSlot(Container container, int index, int x, int y) {
            super(container, index, x, y);
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            // The rules live here, not in the container: on the client it's a plain SimpleContainer.
            return getContainerSlot() == CartridgeWriterBlockEntity.MAIN
                    ? CartridgeWriterBlockEntity.isBlank(stack) || CartridgeWriterBlockEntity.isBurned(stack)
                    : CartridgeWriterBlockEntity.isBurned(stack);
        }

        @Override
        public int getMaxStackSize() {
            return 1;
        }
    }
}
