package com.nanobuntv.cartridge.block;

import com.nanobuntv.cartridge.util.Compat;
import com.nanobuntv.cartridge.CartridgeSounds;
import com.nanobuntv.cartridge.blockentity.CartridgeBlockEntities;
import com.nanobuntv.cartridge.blockentity.ConsoleBlockEntity;
import com.nanobuntv.cartridge.emu.ConsoleKind;
import com.nanobuntv.cartridge.item.CartridgeComponents;
import com.nanobuntv.cartridge.item.CartridgeContents;
import com.nanobuntv.cartridge.item.CartridgeItems;
import com.nanobuntv.cartridge.item.PocketronAdapterItem;
import com.nanobuntv.cartridge.net.EnterPlayModePayload;
import com.nanobuntv.cartridge.netplay.GameKey;
import com.nanobuntv.cartridge.netplay.NetSessions;
import com.nanobuntv.cartridge.platform.Services;
import com.nanobuntv.cartridge.util.Use;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * A console (D11, D136): POWER and RESET are their own click zones (D28), pads and accessories plug into the ports
 * by right-clicking them, and right-clicking a plugged pad takes that player slot (D19, D62). A cartridge goes in by
 * right-clicking the console with it (if it's a game for this console, D143); anywhere else opens the GUI (D29).
 * Each console has its own model, {@link ConsoleLayout} and the pads that fit it.
 */
public abstract class ConsoleBlock extends DeviceBlock implements EntityBlock {
    /** Whether an item plugs into a port. */
    protected enum Plug { YES, WRONG_PORT, OTHER_CONSOLE, NO }

    protected ConsoleBlock(Properties properties) {
        super(properties);
    }

    public abstract ConsoleKind kind();

    public abstract ConsoleLayout layout();

    /** Whether {@code stack} plugs into {@code port} of this console. */
    protected abstract Plug plug(ItemStack stack, int port);

    /**
     * Players whose controls are built into the console (an arcade cabinet's panel, D202): ports below this always
     * have a pad and nothing plugs into them. None on a console with pads.
     */
    public int builtInPlayers() {
        return 0;
    }

    /** POWER is the GUI's alone (an arcade cabinet, D203), not a part of the model to click. */
    public boolean powerInGui() {
        return false;
    }

    /** The block entity type this console's block entity has: Cartridge's own, or an add-on's (D195). */
    public BlockEntityType<? extends ConsoleBlockEntity> blockEntityType() {
        return CartridgeBlockEntities.CONSOLE_8BIT.get();
    }

    /** The space holding the block entity, for a console that fills several (an arcade cabinet). */
    public BlockPos anchor(BlockState state, BlockPos pos) {
        return pos;
    }

    /**
     * The console's own speakers, for one with its own screen (an arcade cabinet, D205): its sound comes from here or
     * from the nearest TV it feeds (D193). Null: the sound comes only from TVs.
     */
    @Nullable
    public Vec3 speaker(BlockState state, BlockPos anchor) {
        return null;
    }

    /** The message for {@link Plug#WRONG_PORT}. */
    protected Component wrongPort(ItemStack stack) {
        return Component.translatable("cartridge_mod.console.wrong_port");
    }

    /** Whether a cartridge (burned) holds a game this console runs; the Super Pixelon takes the Pocketron Adapter (D166). */
    public boolean takes(ItemStack cartridge) {
        if (PocketronAdapterItem.isAdapter(cartridge)) return kind() == ConsoleKind.SUPER_PIXELON;
        CartridgeContents contents = CartridgeComponents.CARTRIDGE.get(cartridge);
        return contents != null && kind().takes(contents.system());
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ConsoleBlockEntity(blockEntityType(), pos, state);
    }

    @Override
    @Nullable
    @SuppressWarnings("unchecked")
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (type != blockEntityType()) return null;
        return (BlockEntityTicker<T>) (level.isClientSide
                ? (BlockEntityTicker<ConsoleBlockEntity>) (l, pos, s, be) -> ConsoleBlockEntity.clientTicker.accept(be)
                : (BlockEntityTicker<ConsoleBlockEntity>) ConsoleBlockEntity::serverTick);
    }

    @Override
    protected Use itemUse(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
            InteractionHand hand, BlockHitResult hit) {
        if (stack.is(CartridgeItems.AV_CABLE.get())) return Use.TO_ITEM;
        if (!(level.getBlockEntity(pos) instanceof ConsoleBlockEntity console)) {
            return Use.EMPTY_HAND;
        }
        ConsolePart part = layout().hit(hit, state.getValue(FACING));
        int port = part.port();
        if (port >= 0) {
            Plug plug = plug(stack, port);
            if (plug != Plug.NO && !(plug == Plug.YES && console.hasPad(port))) {
                if (!level.isClientSide) {
                    switch (plug) {
                        case WRONG_PORT -> Compat.overlay(player, wrongPort(stack));
                        case OTHER_CONSOLE -> Compat.overlay(player, Component.translatable("cartridge_mod.console.other_console", getName()));
                        default -> {
                            console.plugPad(port, Compat.takeOne(stack, player));
                            Compat.overlay(player, Component.translatable("cartridge_mod.console.plugged", port + 1));
                        }
                    }
                }
                return Use.SIDED;
            }
        }
        if (ConsoleBlockEntity.isCartridge(stack) && console.cartridge().isEmpty() && part == ConsolePart.BODY) {
            if (!takes(stack)) {
                if (!level.isClientSide) {
                    Compat.overlay(player, Component.translatable("cartridge_mod.console.wrong_cart", getName()));
                }
                return Use.SIDED;
            }
            if (!level.isClientSide) {
                console.setItem(0, Compat.takeOne(stack, player)); // the console plays the sound (D108)
            }
            return Use.SIDED;
        }
        return Use.EMPTY_HAND;
    }

    @Override
    protected Use emptyUse(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!(level.getBlockEntity(pos) instanceof ConsoleBlockEntity console)) return Use.PASS;
        ConsolePart part = layout().hit(hit, state.getValue(FACING));
        if (level.isClientSide) return Use.SUCCESS;

        switch (part) {
            case POWER -> {
                console.togglePower();
                CartridgeSounds.play(level, pos, console.powered() ? CartridgeSounds.POWER_ON : CartridgeSounds.POWER_OFF, 0.7F);
            }
            case RESET -> {
                console.pressReset();
                CartridgeSounds.play(level, pos, CartridgeSounds.RESET, 0.7F);
            }
            case PORT_1, PORT_2, PORT_3, PORT_4 -> usePort(console, part.port(), level, pos, (ServerPlayer) player);
            case BODY -> player.openMenu(console);
        }
        return Use.CONSUME;
    }

    /** Takes the player slot of a port, or (sneaking) unplugs its pad; {@code pos} is the console's block entity's. */
    protected static void usePort(ConsoleBlockEntity console, int port, Level level, BlockPos pos, ServerPlayer player) {
        if (!console.hasPad(port)) {
            Compat.overlay(player, Component.translatable("cartridge_mod.console.port_empty", port + 1));
            return;
        }
        UUID holder = console.holder(port);
        boolean mine = player.getUUID().equals(holder);
        if (player.isSecondaryUseActive() && port >= console.builtInPlayers()) {
            // D62: sneak + right-click with an empty hand unplugs the pad.
            if (holder != null && !mine) {
                Compat.overlay(player, Component.translatable("cartridge_mod.console.port_taken", holderName(level, holder)));
                return;
            }
            ItemStack pad = console.unplugPad(port);
            if (!player.getInventory().add(pad)) Compat.drop(player, pad);
            Compat.overlay(player, Component.translatable("cartridge_mod.console.unplugged", port + 1));
            return;
        }
        if (holder != null && !mine) {
            Compat.overlay(player, Component.translatable("cartridge_mod.console.port_taken", holderName(level, holder)));
            return;
        }
        console.take(port, player.getUUID());
        Services.NETWORK.sendToPlayer(player, new EnterPlayModePayload(GameKey.console(pos), port));
    }

    private static Component holderName(Level level, UUID holder) {
        Player player = level.getPlayerByUUID(holder);
        return player != null ? player.getDisplayName() : Component.translatable("cartridge_mod.console.someone");
    }

    @Override
    protected void removing(BlockState state, Level level, BlockPos pos) {
        if (level.getBlockEntity(pos) instanceof ConsoleBlockEntity console) {
            console.dropAndUnlink(level, pos);
            if (level instanceof ServerLevel server) NetSessions.broken(server, pos);
        }
    }

    @Override
    public boolean hasAnalogOutputSignal(BlockState state) {
        return false; // no redstone (D27)
    }
}
