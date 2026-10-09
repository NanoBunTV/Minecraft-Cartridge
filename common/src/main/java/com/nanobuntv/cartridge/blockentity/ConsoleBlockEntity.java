package com.nanobuntv.cartridge.blockentity;

import com.nanobuntv.cartridge.block.DeviceBlock;
import com.nanobuntv.cartridge.config.ControlSet;
import com.nanobuntv.cartridge.CartridgeSounds;
import com.nanobuntv.cartridge.config.CartridgeConfig;
import com.nanobuntv.cartridge.block.Console8BitBlock;
import com.nanobuntv.cartridge.block.ConsoleBlock;
import com.nanobuntv.cartridge.block.DisplayBlock;
import com.nanobuntv.cartridge.emu.ConsoleKind;
import com.nanobuntv.cartridge.item.CartridgeComponents;
import com.nanobuntv.cartridge.item.CartridgeContents;
import com.nanobuntv.cartridge.item.CartridgeItems;
import com.nanobuntv.cartridge.item.GameMedia;
import com.nanobuntv.cartridge.item.MemoryCardItem;
import com.nanobuntv.cartridge.menu.ConsoleMenu;
import com.nanobuntv.cartridge.item.PocketronAdapterItem;
import com.nanobuntv.cartridge.netplay.GameHost;
import com.nanobuntv.cartridge.netplay.GameKey;
import com.nanobuntv.cartridge.netplay.NetSessions;
import com.nanobuntv.cartridge.netplay.SessionTimeline;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.Containers;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
//? if >=1.21.6 {
/*import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
*///?}

/**
 * A console's server-side state (every console, D136), synced to clients in full: the cartridge slot (D29; hoppers may insert and
 * extract, D64), power (D28), the controller ports (D19, D62) and who holds each pad, and the linked TVs (D18, D61).
 * The Memory Card slot (D92) is the GUI's alone: hoppers can't reach it and it isn't sent to every client.
 * Emulation itself runs on each nearby client (D13), driven by this state (client/ClientConsoles); the networked game
 * (inputs, RESET, disk swaps, savestates) is netplay/NetSessions', which hears of every change here.
 */
public class ConsoleBlockEntity extends BlockEntity implements WorldlyContainer, MenuProvider, GameHost {
    /** Room for the most ports a console has; {@link #ports()} is this one's (D189). */
    public static final int PORTS = ConsoleKind.MAX_PORTS;
    /** A player farther than this from the console lets go of their pad. */
    private static final double MAX_HOLD_DISTANCE = 8.0;
    public static final int CART_SLOT = 0, CARD_SLOT = 1;
    private static final int[] SLOTS = {CART_SLOT};

    /** Set by the client: called every client tick for each loaded console (client/ClientConsoles). */
    public static Consumer<ConsoleBlockEntity> clientTicker = be -> {};

    private ItemStack cartridge = ItemStack.EMPTY;
    private ItemStack memoryCard = ItemStack.EMPTY;
    private boolean powered;
    /**
     * A fresh random id for each start of a game from power-on (POWER, or RESET with no game running), so clients
     * notice every one even between syncs, and even when a console is replaced in the same spot (a counter would
     * start over and repeat). RESET during a game and disk swaps are frames of the game itself (netplay/NetSessions).
     */
    private int bootId;
    /** The cartridge last seen in the slot, to notice it being swapped while no game runs. */
    @Nullable
    private UUID seenCart;
    private final ItemStack[] pads = {ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY};
    private final UUID[] holders = new UUID[PORTS];
    private final List<BlockPos> tvs = new ArrayList<>();
    /** The cart, pads and holders as last heard ({@link #sounds()}), to play a sound for whatever changed; -1 unknown. */
    private int heard = -1;

    /** What the GUI shows (ConsoleMenu's data): partly per player, so one per open menu. */
    private ContainerData menuData(Player player) {
        return new ContainerData() {
            @Override
            public int get(int index) {
                return switch (index) {
                    case 0 -> powered ? 1 : 0;
                    case 1, 2 -> portState(index - 1);
                    case 3 -> canSwapDisk() ? 1 : 0;
                    case 4 -> level instanceof ServerLevel && NetSessions.running(ConsoleBlockEntity.this) ? 1 : 0;
                    case 5 -> holders[0] == null || holders[0].equals(player.getUUID()) ? 1 : 0; // D93
                    case 8, 9 -> ports() > index - 6 ? portState(index - 6) : 0; // ports 3 and 4 (D189)
                    case 10 -> ports();
                    case 11 -> ControlSet.of(blockKind()).index(); // its controls (D191)
                    case 12 -> (powerInGui() ? ConsoleMenu.FLAG_POWER_IN_GUI : 0)
                            | (builtInPlayers() > 0 ? ConsoleMenu.FLAG_BUILT_IN : 0); // an arcade cabinet's (D202, D203)
                    default -> 0;
                };
            }

            @Override
            public void set(int index, int value) {}

            @Override
            public int getCount() {
                return ConsoleMenu.DATA_COUNT;
            }
        };
    }

    private int portState(int port) {
        return !hasPad(port) ? 0 : holders[port] != null ? 2 : 1;
    }

    public ConsoleBlockEntity(BlockPos pos, BlockState state) {
        this(CartridgeBlockEntities.CONSOLE_8BIT.get(), pos, state);
    }

    /** For an add-on's console blocks, which have a block entity type of their own (D195). */
    public ConsoleBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    // --- State -------------------------------------------------------------------------------------------------

    public ItemStack cartridge() {
        return cartridge;
    }

    @Override
    public boolean powered() {
        return powered;
    }

    @Override
    public int bootId() {
        return bootId;
    }

    /** The game's contents (a cartridge's, or the one in a Pocketron Adapter, D166), or null without a game. */
    @Override
    @Nullable
    public CartridgeContents contents() {
        return PocketronAdapterItem.game(cartridge);
    }

    /** The Memory Card in the GUI's card slot (D92), or empty. */
    @Override
    public ItemStack memoryCard() {
        return memoryCard;
    }

    /** The card's savestate changed: save the console, nothing to sync (the open menus send slot changes). */
    public void cardChanged() {
        super.setChanged();
    }

    @Override
    public void setMemoryCard(ItemStack card) {
        memoryCard = card;
        cardChanged();
    }

    // --- GameHost (netplay) ----------------------------------------------------------------------------------------

    @Override
    public GameKey gameKey() {
        return GameKey.console(worldPosition);
    }

    @Override
    public ServerLevel serverLevel() {
        return (ServerLevel) level;
    }

    @Override
    public Vec3 where() {
        return Vec3.atCenterOf(worldPosition);
    }

    /** Clients leave at the spectator radius from the console or its TVs; past this they've gone without saying. */
    @Override
    public double reach() {
        return CartridgeConfig.server().spectatorRadius + 64.0;
    }

    /** This console's controller ports: two, or four on the Pixelon 64 (D189). */
    public int ports() {
        return blockKind().ports();
    }

    /** A pad is plugged into the port, or the console has that player's controls built in (D202). */
    public boolean hasPad(int port) {
        return port < builtInPlayers() || !pads[port].isEmpty();
    }

    /** Players whose controls are built in (an arcade cabinet's panel, D202). */
    public int builtInPlayers() {
        return getBlockState().getBlock() instanceof ConsoleBlock block ? block.builtInPlayers() : 0;
    }

    /** POWER is in the GUI (an arcade cabinet, D203). */
    public boolean powerInGui() {
        return getBlockState().getBlock() instanceof ConsoleBlock block && block.powerInGui();
    }

    /** What's plugged into a port: a Pixelon Pad, the Pixelon Blaster (port 2 only, D99), or empty. */
    public ItemStack padItem(int port) {
        return pads[port];
    }

    /** The light gun is plugged into port 2 (D99). */
    public boolean hasLightGun() {
        return pads[1].is(CartridgeItems.LIGHT_GUN.get());
    }

    /** The Super Pixelon Mouse is plugged into this port (D144). */
    public boolean hasMouse(int port) {
        return pads[port].is(CartridgeItems.MOUSE_16BIT.get());
    }

    /** What the ports hold, as a netplay device word (SessionTimeline: the light gun D99, the mouse D144). */
    @Override
    public int deviceWord() {
        int word = 0;
        for (int port = 0; port < ports(); port++) {
            int device = pads[port].is(CartridgeItems.LIGHT_GUN.get()) ? SessionTimeline.DEVICE_GUN
                    : hasMouse(port) ? SessionTimeline.DEVICE_MOUSE : SessionTimeline.DEVICE_PAD;
            word |= device << 2 * port;
        }
        return word;
    }

    /** Which console this is (D136): the block's, or a Super Game Boy while the Pocketron Adapter is in (D167). */
    public ConsoleKind kind() {
        if (PocketronAdapterItem.isAdapter(cartridge)) return ConsoleKind.POCKETRON_ADAPTER;
        return blockKind();
    }

    /** The console the block is, whatever is in its slot. */
    public ConsoleKind blockKind() {
        return getBlockState().getBlock() instanceof ConsoleBlock block ? block.kind() : ConsoleKind.PIXELON;
    }

    @Override
    @Nullable
    public UUID holder(int port) {
        return holders[port];
    }

    public List<BlockPos> tvs() {
        return Collections.unmodifiableList(tvs);
    }

    public void togglePower() {
        powered = !powered;
        if (powered) bootId = newId(bootId);
        else forgetResume();
        if (level != null && !level.isClientSide) {
            for (BlockPos tv : tvs) DisplayBlock.setLit(level, tv, powered); // D105
        }
        changed();
    }

    /** RESET (D28): a frame of the running game; with no game running (say, after a cart was pulled), a fresh start. */
    public void pressReset() {
        if (!powered || NetSessions.reset(this)) return;
        reboot();
    }

    /** Starts the game again from power-on (also when the session's core turns out to be old, D89). */
    @Override
    public void reboot() {
        if (!powered) return;
        bootId = newId(bootId);
        forgetResume();
        changed();
    }

    private void forgetResume() {
        if (level instanceof ServerLevel server) NetSessions.forget(server, gameKey());
    }

    /** The disk swap button shows only while a disk game is in and the console is on (D74). */
    public boolean canSwapDisk() {
        CartridgeContents contents = contents();
        return powered && contents != null && CartridgeContents.FDS.equals(contents.system());
    }

    /** Ejects the disk and inserts the next side: a frame of the game (netplay/RollbackEngine runs the sequence). */
    public void swapDisk() {
        if (canSwapDisk()) NetSessions.swapDisk(this);
    }

    public void plugPad(int port, ItemStack pad) {
        pads[port] = pad;
        changed();
    }

    public ItemStack unplugPad(int port) {
        ItemStack pad = pads[port];
        pads[port] = ItemStack.EMPTY;
        holders[port] = null;
        changed();
        return pad;
    }

    /** Takes a player slot (D19); a player holds at most one pad per console. */
    public void take(int port, UUID player) {
        for (int i = 0; i < PORTS; i++) {
            if (player.equals(holders[i])) holders[i] = null;
        }
        holders[port] = player;
        changed();
    }

    public void release(UUID player) {
        boolean any = false;
        for (int i = 0; i < PORTS; i++) {
            if (player.equals(holders[i])) {
                holders[i] = null;
                any = true;
            }
        }
        if (any) changed();
    }

    public boolean addTv(BlockPos tv) {
        if (tvs.contains(tv)) return false;
        tvs.add(tv.immutable());
        changed();
        return true;
    }

    public void removeTv(BlockPos tv) {
        if (tvs.remove(tv)) changed();
    }

    /** Drops the cartridge, the Memory Card, the pads and one AV Cable per linked TV, and unlinks the TVs (block broken). */
    public void dropAndUnlink(Level level, BlockPos pos) {
        Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(), cartridge);
        Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(), memoryCard);
        memoryCard = ItemStack.EMPTY;
        for (ItemStack pad : pads) Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(), pad);
        for (BlockPos tv : tvs) {
            if (level.getBlockEntity(tv) instanceof TvBlockEntity tvEntity) tvEntity.setConsole(null);
            Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(), new ItemStack(CartridgeItems.AV_CABLE.get()));
        }
        cartridge = ItemStack.EMPTY;
        tvs.clear();
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, ConsoleBlockEntity be) {
        NetSessions.tick(be);
        // The Pixelon's door stands open while a cartridge is in (D188).
        if (state.hasProperty(Console8BitBlock.LOADED) && state.getValue(Console8BitBlock.LOADED) == be.cartridge.isEmpty()) {
            level.setBlock(pos, state.setValue(Console8BitBlock.LOADED, !be.cartridge.isEmpty()), Block.UPDATE_CLIENTS);
        }
        if (level.getGameTime() % 10 != 0) return;
        for (int i = 0; i < be.ports(); i++) {
            UUID holder = be.holders[i];
            if (holder == null) continue;
            Player player = level.getPlayerByUUID(holder);
            if (player == null || player.isSpectator()
                    || player.distanceToSqr(Vec3.atCenterOf(pos)) > MAX_HOLD_DISTANCE * MAX_HOLD_DISTANCE) {
                be.holders[i] = null;
                be.changed();
            }
        }
    }

    private int newId(int previous) {
        int id;
        do {
            id = level != null ? level.getRandom().nextInt() : (int) System.nanoTime();
        } while (id == previous || id == 0);
        return id;
    }

    private void changed() {
        setChanged();
    }

    @Override
    public void setChanged() {
        super.setChanged();
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
            CartridgeContents contents = contents();
            UUID cart = contents != null ? contents.cartId() : null;
            if (powered && !java.util.Objects.equals(cart, seenCart)) forgetResume(); // pulled or swapped
            seenCart = cart;
            sounds();
            NetSessions.changed(this);
        }
    }

    /**
     * D108: a sound for each change since the last call, however it came about (a hand, the GUI, a hopper): the cart
     * going in or out, a pad or the Blaster plugged in or out, a pad taken or put down.
     */
    private void sounds() {
        int now = soundState();
        int was = heard;
        heard = now;
        if (was < 0 || was == now || level == null) return;
        if (bit(now, 0) != bit(was, 0)) CartridgeSounds.play(level, worldPosition, bit(now, 0) ? CartridgeSounds.CART_INSERT : CartridgeSounds.CART_EJECT, 1.0F);
        for (int i = 0; i < PORTS; i++) {
            boolean plugged = bit(now, 1 + i), held = bit(now, 1 + PORTS + i);
            if (plugged != bit(was, 1 + i)) {
                CartridgeSounds.play(level, worldPosition, plugged ? CartridgeSounds.PLUG : CartridgeSounds.UNPLUG, 0.8F);
            } else if (plugged && held != bit(was, 1 + PORTS + i)) {
                CartridgeSounds.play(level, worldPosition, held ? CartridgeSounds.PAD_TAKE : CartridgeSounds.PAD_PUT_DOWN, 0.6F);
            }
        }
    }

    private int soundState() {
        int bits = cartridge.isEmpty() ? 0 : 1;
        for (int i = 0; i < PORTS; i++) {
            if (!pads[i].isEmpty()) bits |= 1 << (1 + i);
            if (holders[i] != null) bits |= 1 << (1 + PORTS + i);
        }
        return bits;
    }

    private static boolean bit(int bits, int index) {
        return (bits >> index & 1) != 0;
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        NetSessions.unloaded(this);
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

    /** The saved fields (an add-on's console adds its own). */
    protected void save(Saved.Out out) {
        out.putStack("cartridge", cartridge);
        out.putStack("memory_card", memoryCard);
        out.putBoolean("powered", powered);
        out.putInt("boot", bootId);
        for (int i = 0; i < PORTS; i++) {
            out.putStack("pad" + i, pads[i]);
            if (holders[i] != null) out.putUUID("holder" + i, holders[i]);
        }
        out.putLongArray("tvs", tvs.stream().mapToLong(BlockPos::asLong).toArray());
    }

    protected void load(Saved.In in) {
        cartridge = in.getStack("cartridge");
        memoryCard = in.getStack("memory_card"); // never on clients: updates leave it out
        powered = in.getBoolean("powered");
        bootId = in.getInt("boot", 0);
        for (int i = 0; i < PORTS; i++) {
            pads[i] = in.getStack("pad" + i);
            holders[i] = in.getUUID("holder" + i);
        }
        tvs.clear();
        for (long tv : in.getLongArray("tvs")) tvs.add(BlockPos.of(tv));
        CartridgeContents contents = contents();
        seenCart = contents != null ? contents.cartId() : null;
        heard = soundState();
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
        CompoundTag tag = Saved.customOnly(this, registries);
        tag.remove("memory_card"); // only the GUI shows it, through its own slot
        return tag;
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    // --- The cartridge slot: menu (D29) and hoppers (D64) ---------------------------------------------------------

    /** A burned cartridge (or another burned game, an Arcade Board, D197), or the Pocketron Adapter (D166). */
    public static boolean isCartridge(ItemStack stack) {
        return GameMedia.ofBurned(stack).isPresent() || PocketronAdapterItem.isAdapter(stack);
    }

    /** A game this console runs (D143); the Super Pixelon also takes the Pocketron Adapter (D166). */
    public boolean takes(ItemStack stack) {
        if (PocketronAdapterItem.isAdapter(stack)) return blockKind() == ConsoleKind.SUPER_PIXELON;
        CartridgeContents contents = CartridgeComponents.CARTRIDGE.get(stack);
        return contents != null && blockKind().takes(contents.system());
    }

    @Override
    public int getContainerSize() {
        return 2;
    }

    @Override
    public boolean isEmpty() {
        return cartridge.isEmpty() && memoryCard.isEmpty();
    }

    @Override
    public ItemStack getItem(int slot) {
        return slot == CARD_SLOT ? memoryCard : cartridge;
    }

    @Override
    public ItemStack removeItem(int slot, int amount) {
        ItemStack removed = getItem(slot);
        if (removed.isEmpty() || amount <= 0) return ItemStack.EMPTY;
        setSlot(slot, ItemStack.EMPTY);
        if (slot == CARD_SLOT) cardChanged();
        else changed();
        return removed;
    }

    @Override
    public ItemStack removeItemNoUpdate(int slot) {
        ItemStack removed = getItem(slot);
        setSlot(slot, ItemStack.EMPTY);
        return removed;
    }

    @Override
    public void setItem(int slot, ItemStack stack) {
        setSlot(slot, stack);
        if (slot == CARD_SLOT) cardChanged();
        else changed();
    }

    private void setSlot(int slot, ItemStack stack) {
        if (slot == CARD_SLOT) memoryCard = stack;
        else cartridge = stack;
    }

    @Override
    public int getMaxStackSize() {
        return 1;
    }

    @Override
    public boolean canPlaceItem(int slot, ItemStack stack) {
        return slot == CARD_SLOT ? MemoryCardItem.isCard(stack) : isCartridge(stack) && takes(stack);
    }

    @Override
    public boolean stillValid(Player player) {
        return Container.stillValidBlockEntity(this, player);
    }

    @Override
    public void clearContent() {
        cartridge = ItemStack.EMPTY;
        memoryCard = ItemStack.EMPTY;
        changed();
    }

    @Override
    public int[] getSlotsForFace(Direction side) {
        return SLOTS;
    }

    @Override
    public boolean canPlaceItemThroughFace(int slot, ItemStack stack, @Nullable Direction side) {
        return slot == CART_SLOT && cartridge.isEmpty() && isCartridge(stack) && takes(stack);
    }

    @Override
    public boolean canTakeItemThroughFace(int slot, ItemStack stack, Direction side) {
        return slot == CART_SLOT; // the Memory Card slot is the GUI's alone (D92)
    }

    @Override
    public Component getDisplayName() {
        return getBlockState().getBlock().getName();
    }

    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player player) {
        return new ConsoleMenu(containerId, inventory, this, menuData(player));
    }
}
