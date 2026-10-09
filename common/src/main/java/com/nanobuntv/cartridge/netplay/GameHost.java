package com.nanobuntv.cartridge.netplay;

import com.nanobuntv.cartridge.item.CartridgeContents;
import java.util.List;
import java.util.UUID;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * What a networked game runs in, server side (D12, D158): a console block entity, or a handheld in its holder's hand
 * (item/Handhelds). {@link NetSession} asks it who holds which port, what the ports hold, where it is, and for the
 * Memory Card its GUI holds.
 */
public interface GameHost {
    GameKey gameKey();

    boolean powered();

    /** A fresh random id for each start from power-on. */
    int bootId();

    /** Starts the game again from power-on (the session's core turned out to be old, D89). */
    void reboot();

    ServerLevel serverLevel();

    /** Where it is: members farther than {@link #reach()} from here have left. */
    Vec3 where();

    /** How far from {@link #where()} a client may be and still be in the game. */
    double reach();

    @Nullable
    UUID holder(int port);

    /** What the ports hold, as a netplay device word (SessionTimeline). */
    int deviceWord();

    /** The game in it, or null. */
    @Nullable
    CartridgeContents contents();

    /**
     * The cartridges whose battery saves this game holds: the game's own, or both of a linked pair (D177), whose saves
     * travel packed together ({@link LinkedSaves}).
     */
    default List<UUID> saveCarts() {
        CartridgeContents contents = contents();
        return contents == null ? List.of() : List.of(contents.cartId());
    }

    /** The Memory Card in its GUI (D92), or empty. */
    ItemStack memoryCard();

    /** The card with a new savestate on it (D92), in place of the one in the GUI. */
    void setMemoryCard(ItemStack card);
}
