package com.nanobuntv.cartridge.menu;

import com.nanobuntv.cartridge.CartridgeSounds;
import com.nanobuntv.cartridge.config.ControlSet;
import com.nanobuntv.cartridge.blockentity.ConsoleBlockEntity;
import com.nanobuntv.cartridge.item.CartridgeItems;
import com.nanobuntv.cartridge.item.MemoryCardItem;
import com.nanobuntv.cartridge.netplay.GameHost;
import com.nanobuntv.cartridge.netplay.GameKey;
import com.nanobuntv.cartridge.netplay.NetSessions;
import java.util.List;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * The console's GUI (D29), and a handheld's (D163): the cartridge slot, the Memory Card slot (D92) and the player's
 * inventory. {@link #data} carries the power state, each port's state (0 empty, 1 pad plugged, 2 pad in use), whether
 * the disk swap button shows (D74), whether a game is running, whether this player may load a card (D93), and whether
 * it's a handheld's (which has a power switch instead of ports), a handheld's link, ports 3 and 4 and how many ports
 * there are (the Pixelon 64 has four, D189), and whose controls it plays with (D191). Save and Load answer with a result code
 * ({@code RESULT_*}) the screen shows; a save finishes later, when the authority's savestate arrives.
 */
public class ConsoleMenu extends HeldItemMenu {
    public static final int DATA_COUNT = 13;
    /** Data 12's bits: POWER is a GUI button (D203); the players' controls are built in (D202). */
    public static final int FLAG_POWER_IN_GUI = 1, FLAG_BUILT_IN = 2;
    public static final int BUTTON_SWAP_DISK = 0, BUTTON_SAVE_CARD = 1, BUTTON_LOAD_CARD = 2, BUTTON_POWER = 3, BUTTON_UNLINK = 4;
    /** A handheld's Link Cable slot (D179), right of the power switch. */
    public static final int LINK_X = 152, LINK_Y = 35;
    /** {@link #linkState()} of a handheld that doesn't link (D184): no Link Cable slot. */
    public static final int LINK_NONE = 3;
    public static final int SLOT_X = 26, SLOT_Y = 35, CARD_X = 26, CARD_Y = 80;
    public static final int INVENTORY_Y = 122;

    public static final int RESULT_NONE = 0, RESULT_SAVING = 1, RESULT_SAVED = 2, RESULT_LOADED = 3, RESULT_NO_GAME = 4,
            RESULT_NO_CARD = 5, RESULT_EMPTY = 6, RESULT_OTHER_GAME = 7, RESULT_PLAYER_ONE = 8, RESULT_CORE = 9,
            RESULT_MISSING = 10, RESULT_CARD_GONE = 11, RESULT_FAILED = 12;
    /** Translation keys for the results, by code. */
    public static final String[] RESULT_KEYS = {"", "saving", "saved", "loaded", "no_game", "no_card", "empty", "other_game",
            "player_one", "core", "missing", "card_gone", "failed"};

    private final Container container;
    private final ContainerData data;
    /** The last result, plus a counter above the low 4 bits so the same one twice still shows (synced as a short). */
    private final DataSlot result = DataSlot.standalone();
    private int results;

    /** Client side. */
    public ConsoleMenu(int containerId, Inventory inventory) {
        this(containerId, inventory, new SimpleContainer(2), new SimpleContainerData(DATA_COUNT));
    }

    /** Client side, a handheld's GUI (D163): a third slot for the Link Cable (D179). */
    public static ConsoleMenu handheld(int containerId, Inventory inventory) {
        return new ConsoleMenu(containerId, inventory, new SimpleContainer(3), new SimpleContainerData(DATA_COUNT), -1);
    }

    public ConsoleMenu(int containerId, Inventory inventory, Container container, ContainerData data) {
        this(containerId, inventory, container, data, -1);
    }

    /** {@code heldSlot}: a handheld's GUI, the inventory slot the handheld is in (it stays put while open). */
    public ConsoleMenu(int containerId, Inventory inventory, Container container, ContainerData data, int heldSlot) {
        super(container.getContainerSize() == 3 ? CartridgeMenus.HANDHELD.get() : CartridgeMenus.CONSOLE_8BIT.get(),
                containerId, heldSlot, container.getContainerSize());
        checkContainerSize(container, 2);
        checkContainerDataCount(data, DATA_COUNT);
        this.container = container;
        this.data = data;
        container.startOpen(inventory.player);
        addSlot(new Slot(container, ConsoleBlockEntity.CART_SLOT, SLOT_X, SLOT_Y) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                // The console's own check (a game for this console, D143); on the client, any cartridge.
                return ConsoleBlockEntity.isCartridge(stack) && container.canPlaceItem(ConsoleBlockEntity.CART_SLOT, stack);
            }

            @Override
            public int getMaxStackSize() {
                return 1;
            }
        });
        addSlot(new Slot(container, ConsoleBlockEntity.CARD_SLOT, CARD_X, CARD_Y) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return MemoryCardItem.isCard(stack);
            }

            @Override
            public int getMaxStackSize() {
                return 1;
            }
        });
        if (container.getContainerSize() == 3) {
            addSlot(new Slot(container, 2, LINK_X, LINK_Y) {
                @Override
                public boolean mayPlace(ItemStack stack) {
                    return linkable() && stack.is(CartridgeItems.LINK_CABLE.get());
                }

                @Override
                public boolean isActive() {
                    return linkable();
                }

                @Override
                public int getMaxStackSize() {
                    return 1;
                }
            });
        }
        addInventory(inventory, INVENTORY_Y);
        addDataSlots(data);
        addDataSlot(result);
    }

    /**
     * A handheld's link (D181): 0 none, 1 linked with both in hand, 2 linked but the other one is away;
     * {@link #LINK_NONE} for one that doesn't link (D184).
     */
    public int linkState() {
        return data.get(7);
    }

    /** A handheld that takes a Link Cable (D176, D184). */
    public boolean linkable() {
        return handheld() && linkState() != LINK_NONE;
    }

    public boolean powered() {
        return data.get(0) != 0;
    }

    /** 0 empty, 1 pad plugged, 2 pad in use. */
    public int portState(int port) {
        return data.get(port < 2 ? 1 + port : 6 + port);
    }

    /** The console's controller ports: 2, or 4 on the Pixelon 64 (D189); 0 for a handheld. */
    public int ports() {
        return data.get(10);
    }

    /** The console family whose controls this console or handheld plays with (D191). */
    public ControlSet controls() {
        int index = data.get(11);
        return ControlSet.byIndex(index);
    }

    public boolean canSwapDisk() {
        return data.get(3) != 0;
    }

    /** Clients are playing a game here, so it can be saved or loaded. */
    public boolean running() {
        return data.get(4) != 0;
    }

    /** Player 1, or nobody holds pad 1 (D93). */
    public boolean mayLoad() {
        return data.get(5) != 0;
    }

    /** A console whose POWER is this GUI's button (an arcade cabinet, D203). */
    public boolean powerInGui() {
        return (data.get(12) & FLAG_POWER_IN_GUI) != 0;
    }

    /** A console whose players' controls are built in (an arcade cabinet, D202): its ports are players. */
    public boolean builtInControls() {
        return (data.get(12) & FLAG_BUILT_IN) != 0;
    }

    /** A handheld's GUI (D163): a power switch, no ports. */
    public boolean handheld() {
        return data.get(6) != 0;
    }

    public ItemStack memoryCard() {
        return slots.get(ConsoleBlockEntity.CARD_SLOT).getItem();
    }

    /** The last Save or Load result: the code in the low 4 bits, a counter above. */
    public int result() {
        return result.get();
    }

    private void setResult(int code) {
        results = (results + 1) & 0x7FF;
        result.set(code | results << 4);
    }

    /** Shows a result in this player's console or handheld GUI, if it's still open on that game. */
    public static void showResult(ServerPlayer player, GameKey game, int code) {
        if (player.containerMenu instanceof ConsoleMenu menu && menu.container instanceof GameHost host
                && host.gameKey().equals(game)) {
            menu.setResult(code);
        }
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (!(container instanceof GameHost host) || !(player instanceof ServerPlayer serverPlayer)) return false;
        switch (id) {
            case BUTTON_SWAP_DISK -> {
                if (!(host instanceof ConsoleBlockEntity console) || !console.canSwapDisk()) return false;
                console.swapDisk();
            }
            case BUTTON_SAVE_CARD -> setResult(host.powered() ? NetSessions.saveCard(host, serverPlayer) : RESULT_NO_GAME);
            case BUTTON_LOAD_CARD -> setResult(host.powered() ? NetSessions.loadCard(host, serverPlayer) : RESULT_NO_GAME);
            case BUTTON_POWER -> {
                if (host instanceof HandheldContainer handheld) {
                    handheld.togglePower();
                } else if (host instanceof ConsoleBlockEntity console && console.powerInGui()) {
                    console.togglePower();
                    CartridgeSounds.play(console.getLevel(), console.getBlockPos(),
                            console.powered() ? CartridgeSounds.POWER_ON : CartridgeSounds.POWER_OFF, 0.7F);
                } else {
                    return false;
                }
            }
            case BUTTON_UNLINK -> {
                if (!(host instanceof HandheldContainer handheld)) return false;
                handheld.unlink();
            }
            default -> {
                return false;
            }
        }
        return true;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return quickMove(index, slots.size() - 36 == 3
                ? List.of(ConsoleBlockEntity::isCartridge, MemoryCardItem::isCard, s -> s.is(CartridgeItems.LINK_CABLE.get()))
                : List.of(ConsoleBlockEntity::isCartridge, MemoryCardItem::isCard));
    }

    @Override
    public boolean stillValid(Player player) {
        return container.stillValid(player);
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        container.stopOpen(player);
    }
}
