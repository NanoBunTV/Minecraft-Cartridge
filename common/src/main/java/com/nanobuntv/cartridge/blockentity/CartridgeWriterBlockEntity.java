package com.nanobuntv.cartridge.blockentity;

import com.nanobuntv.cartridge.block.DeviceBlock;
import com.nanobuntv.cartridge.item.CartridgeComponents;
import com.nanobuntv.cartridge.item.CartridgeContents;
import com.nanobuntv.cartridge.rom.RomServer;
import com.nanobuntv.cartridge.item.CartridgeItem;
import com.nanobuntv.cartridge.item.GameMedia;
import com.nanobuntv.cartridge.menu.WriterMenu;
import com.nanobuntv.cartridge.save.SaveServer;
import com.nanobuntv.cartridge.util.Compat;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.Containers;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;
//? if >=1.21.6 {
/*import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
*///?}

/**
 * The Cartridge Writer (D15, D45): a main slot (a Blank Cartridge to burn or copy onto, or a burned cart to erase,
 * D78) and a source slot (a burned cart to copy, D73, D81). Burning, copying and erasing are instant (D80). The slots
 * are synced to clients so the cart shows in the socket, along with {@link #busy()} for the status LED. Hoppers can't
 * reach the slots: everything here happens through the GUI.
 */
public class CartridgeWriterBlockEntity extends BlockEntity implements WorldlyContainer, MenuProvider {
    public static final int MAIN = 0, SOURCE = 1;
    private static final int[] NO_SLOTS = {};

    private final NonNullList<ItemStack> items = NonNullList.withSize(2, ItemStack.EMPTY);
    /** An upload is on its way to this Writer (status LED). Not saved. */
    private boolean busy;

    public CartridgeWriterBlockEntity(BlockPos pos, BlockState state) {
        super(CartridgeBlockEntities.CARTRIDGE_WRITER.get(), pos, state);
    }

    /** A blank to burn onto: a Blank Cartridge, or an add-on's (a Blank Arcade Board, D197). */
    public static boolean isBlank(ItemStack stack) {
        return GameMedia.ofBlank(stack).isPresent();
    }

    /** What the blank in the main slot is, if there is one. */
    public Optional<GameMedia> blankMedia() {
        return GameMedia.ofBlank(items.get(MAIN));
    }

    /**
     * A burned cartridge (or board). Not the Pocketron Adapter (D217): a console takes it like a cartridge, the Writer
     * doesn't.
     */
    public static boolean isBurned(ItemStack stack) {
        return GameMedia.ofBurned(stack).isPresent();
    }

    public boolean busy() {
        return busy;
    }

    public void setBusy(boolean busy) {
        if (this.busy == busy) return;
        this.busy = busy;
        setChanged();
    }

    /** True if the main slot holds a blank to burn onto. */
    public boolean hasBlank() {
        return isBlank(items.get(MAIN));
    }

    /** Burns a new cartridge onto the blank in the main slot (D15, D80). */
    public boolean burn(CartridgeContents contents) {
        GameMedia media = blankMedia().orElse(null);
        if (media == null || !media.takes(contents.system())) return false;
        items.set(MAIN, media.create(contents));
        setChanged();
        return true;
    }

    /**
     * Copies the source cart onto the blank (D73): same game, its own ID, and no battery save (D216): a copy to give
     * away starts fresh. A save goes from one cart to another through Export and Import.
     */
    public boolean copy() {
        ItemStack source = items.get(SOURCE);
        CartridgeContents contents = CartridgeComponents.CARTRIDGE.get(source);
        GameMedia media = blankMedia().orElse(null);
        // Only onto the same kind of blank: a cartridge's game onto a Blank Cartridge, a board's onto a blank board.
        if (media == null || contents == null || GameMedia.ofBurned(source).orElse(null) != media) return false;
        UUID id = UUID.randomUUID();
        ItemStack copy = media.create(new CartridgeContents(contents.system(), contents.romSha1(), contents.title(), id));
        Compat.copyCustomName(source, copy);
        items.set(MAIN, copy);
        setChanged();
        return true;
    }

    /** Turns the burned cart (or board) in the main slot back into a blank (D78); its battery save goes too. */
    public boolean erase() {
        CartridgeContents contents = CartridgeComponents.CARTRIDGE.get(items.get(MAIN));
        GameMedia media = GameMedia.ofBurned(items.get(MAIN)).orElse(null);
        if (contents == null || media == null) return false;
        if (level instanceof ServerLevel server) SaveServer.store(server.getServer()).deleteSram(contents.cartId());
        items.set(MAIN, media.blankStack());
        setChanged();
        return true;
    }

    public void drop(Level level, BlockPos pos) {
        Containers.dropContents(level, pos, items);
        items.clear();
    }

    @Override
    public void setChanged() {
        super.setChanged();
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
            // D124: a burned cart put in whose game file was removed by hand: the Writer offers to restore it.
            CartridgeContents main = CartridgeComponents.CARTRIDGE.get(items.get(MAIN));
            if (main != null && level.getServer() != null) RomServer.checkStored(level.getServer(), main.romSha1());
        }
    }

    // --- Saving and syncing --------------------------------------------------------------------------------------

    //? if >=1.21.6 {
    /*@Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        save(new Saved.Out(output));
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        load(new Saved.In(input));
    }
    *///?} else if >=1.20.5 {
    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        save(new Saved.Out(tag, registries));
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        load(new Saved.In(tag, registries));
    }
    //?} else {
    /*@Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        save(new Saved.Out(tag, null));
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        load(new Saved.In(tag, null));
    }
    *///?}

    //? if >=1.21.5 {
    /*// The block's removal side effects (dropping what's inside, unlinking), while this is still here (DeviceBlock).
    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        if (level != null && state.getBlock() instanceof DeviceBlock device) device.blockEntityRemoving(state, level, pos);
    }
    *///?}

    private void save(Saved.Out out) {
        out.putItems(items, false);
    }

    private void load(Saved.In in) {
        items.clear();
        in.getItems(items);
        busy = in.getBoolean("busy");
    }

    //? if >=1.20.5 {
    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return updateTag(registries);
    }
    //?} else {
    /*@Override
    public CompoundTag getUpdateTag() {
        return updateTag(null);
    }
    *///?}

    private CompoundTag updateTag(HolderLookup.Provider registries) {
        return Saved.tag(registries, out -> {
            out.putItems(items, true);
            out.putBoolean("busy", busy);
        });
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    // --- Container (the menu's slots) ----------------------------------------------------------------------------

    @Override
    public int getContainerSize() {
        return items.size();
    }

    @Override
    public boolean isEmpty() {
        return items.stream().allMatch(ItemStack::isEmpty);
    }

    @Override
    public ItemStack getItem(int slot) {
        return items.get(slot);
    }

    @Override
    public ItemStack removeItem(int slot, int amount) {
        ItemStack removed = ContainerHelper.removeItem(items, slot, amount);
        if (!removed.isEmpty()) setChanged();
        return removed;
    }

    @Override
    public ItemStack removeItemNoUpdate(int slot) {
        return ContainerHelper.takeItem(items, slot);
    }

    @Override
    public void setItem(int slot, ItemStack stack) {
        items.set(slot, stack);
        if (stack.getCount() > 1) stack.setCount(1);
        setChanged();
    }

    @Override
    public int getMaxStackSize() {
        return 1;
    }

    @Override
    public boolean canPlaceItem(int slot, ItemStack stack) {
        return slot == MAIN ? isBlank(stack) || isBurned(stack) : isBurned(stack);
    }

    @Override
    public boolean stillValid(Player player) {
        return Container.stillValidBlockEntity(this, player);
    }

    @Override
    public void clearContent() {
        items.clear();
        setChanged();
    }

    @Override
    public int[] getSlotsForFace(Direction side) {
        return NO_SLOTS;
    }

    @Override
    public boolean canPlaceItemThroughFace(int slot, ItemStack stack, @Nullable Direction side) {
        return false;
    }

    @Override
    public boolean canTakeItemThroughFace(int slot, ItemStack stack, Direction side) {
        return false;
    }

    @Override
    public Component getDisplayName() {
        return getBlockState().getBlock().getName();
    }

    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player player) {
        return new WriterMenu(containerId, inventory, this);
    }
}
