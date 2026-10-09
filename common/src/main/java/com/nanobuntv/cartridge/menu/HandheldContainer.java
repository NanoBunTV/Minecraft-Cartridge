package com.nanobuntv.cartridge.menu;

import com.nanobuntv.cartridge.config.ControlSet;
import com.nanobuntv.cartridge.item.CartridgeComponents;
import com.nanobuntv.cartridge.item.CartridgeContents;
import com.nanobuntv.cartridge.item.CartridgeItems;
import com.nanobuntv.cartridge.item.HandheldItem;
import com.nanobuntv.cartridge.item.HandheldLink;
import com.nanobuntv.cartridge.item.HandheldState;
import com.nanobuntv.cartridge.item.Handhelds;
import com.nanobuntv.cartridge.item.MemoryCardItem;
import com.nanobuntv.cartridge.netplay.GameHost;
import com.nanobuntv.cartridge.netplay.GameKey;
import com.nanobuntv.cartridge.netplay.NetSessions;
import com.nanobuntv.cartridge.util.Compat;
import java.util.UUID;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * A handheld's GUI slots (D163): the cartridge and a Memory Card, inside the handheld in the player's hand. It's the
 * game's {@link GameHost} too, for the GUI's Save and Load (D92) and the power switch (D164).
 */
public class HandheldContainer extends HeldItemContainer implements GameHost {
    private final ServerPlayer serverPlayer;
    private final HandheldItem item;

    /** The game in it when the GUI opened or last changed, to notice a cart swapped while linked. */
    @Nullable
    private CartridgeContents linkedGame;

    public HandheldContainer(ServerPlayer player, int inventorySlot, HandheldItem item) {
        super(player, inventorySlot, HandheldItem.SLOTS, stack -> stack.is(item));
        this.serverPlayer = player;
        this.item = item;
        this.linkedGame = HandheldItem.game(holder());
    }

    @Nullable
    private HandheldState state() {
        return HandheldItem.state(holder());
    }

    /** What the GUI shows (ConsoleMenu's data): power, no ports, whether a game runs, whether this player may load. */
    public ContainerData menuData() {
        return new ContainerData() {
            @Override
            public int get(int index) {
                return switch (index) {
                    case 0 -> powered() ? 1 : 0;
                    case 4 -> NetSessions.running(HandheldContainer.this) ? 1 : 0;
                    case 5 -> holder(0) == null || serverPlayer.getUUID().equals(holder(0)) ? 1 : 0;
                    case 6 -> 1; // a handheld
                    case 7 -> linkState();
                    case 11 -> ControlSet.of(item.kind()).index(); // its controls (D191)
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

    /** 0 not linked, 1 linked and both in hand, 2 linked but the other one is away (D181), 3 it doesn't link (D184). */
    private int linkState() {
        if (!item.linkable()) return ConsoleMenu.LINK_NONE;
        HandheldLink link = CartridgeComponents.HANDHELD_LINK.get(holder());
        if (link == null) return 0;
        return Handhelds.host(Compat.server(serverPlayer), link.linkId()) != null ? 1 : 2;
    }

    /** The GUI's power switch (D164). */
    void togglePower() {
        if (state() != null) HandheldItem.setPower(serverPlayer, holder(), !powered());
    }

    @Override
    protected void changed() {
        ItemStack handheld = holder();
        HandheldLink link = CartridgeComponents.HANDHELD_LINK.get(handheld);
        CartridgeContents game = HandheldItem.game(handheld);
        if (link != null && (game == null || !game.equals(linkedGame))) { // another cart: the linked game is over (D177)
            Handhelds.unlink(Compat.server(serverPlayer), link, Component.translatable("cartridge_mod.link.unlinked"));
        }
        linkedGame = game;
        HandheldItem.changed(serverPlayer, handheld); // a cart pulled while on ends the game, as on a console (D30)
    }

    @Override
    public boolean canPlaceItem(int slot, ItemStack stack) {
        return switch (slot) {
            case HandheldItem.CARD_SLOT -> MemoryCardItem.isCard(stack);
            case HandheldItem.LINK_SLOT -> item.linkable() && stack.is(CartridgeItems.LINK_CABLE.get());
            default -> item.takes(stack);
        };
    }

    /** The GUI's Unlink (D177). */
    void unlink() {
        HandheldLink link = CartridgeComponents.HANDHELD_LINK.get(holder());
        if (link != null) Handhelds.unlink(Compat.server(serverPlayer), link, Component.translatable("cartridge_mod.link.unlinked"));
    }

    // --- GameHost ------------------------------------------------------------------------------------------------------

    @Nullable
    private GameHost host() {
        HandheldState state = state();
        return state == null ? null : Handhelds.host(Compat.server(serverPlayer), state.id());
    }

    @Override
    public GameKey gameKey() {
        HandheldState state = state();
        return GameKey.handheld(state != null ? state.id() : new UUID(0, 0));
    }

    @Override
    public boolean powered() {
        HandheldState state = state();
        return state != null && state.powered();
    }

    @Override
    public int bootId() {
        HandheldState state = state();
        return state != null ? state.bootId() : 0;
    }

    @Override
    public void reboot() {
        GameHost host = host();
        if (host != null) host.reboot();
    }

    @Override
    public ServerLevel serverLevel() {
        return Compat.level(serverPlayer);
    }

    @Override
    public Vec3 where() {
        return serverPlayer.position();
    }

    @Override
    public double reach() {
        GameHost host = host();
        return host != null ? host.reach() : 0;
    }

    @Override
    @Nullable
    public UUID holder(int port) {
        GameHost host = host();
        return host != null ? host.holder(port) : null;
    }

    @Override
    public int deviceWord() {
        return 0;
    }

    @Override
    @Nullable
    public CartridgeContents contents() {
        return HandheldItem.game(holder());
    }

    @Override
    public ItemStack memoryCard() {
        return getItem(HandheldItem.CARD_SLOT);
    }

    @Override
    public void setMemoryCard(ItemStack card) {
        setItem(HandheldItem.CARD_SLOT, card);
    }
}
