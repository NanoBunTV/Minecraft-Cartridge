package com.nanobuntv.cartridge.block;

import com.nanobuntv.cartridge.util.Compat;
import com.nanobuntv.cartridge.CartridgeSounds;
import com.nanobuntv.cartridge.blockentity.ConsoleBlockEntity;
import com.nanobuntv.cartridge.blockentity.TvBlockEntity;
import com.nanobuntv.cartridge.item.CartridgeItems;
import com.nanobuntv.cartridge.util.Use;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * Anything an AV Cable can feed (D18): the CRT TV, the flatscreens (D129) and the Display Panels of a video wall
 * (D131). Each display has one <b>anchor</b>, the space whose {@link TvBlockEntity} holds the cable's link: the
 * block itself, the Large Flatscreen's main space, or a wall's linked panel. Consoles list their displays by anchor.
 * <p>
 * Shared behaviour: lit while its console is on (light level 7, D105); sneak + right-click with an empty hand
 * unplugs the cable and gives it back; breaking the anchor drops the cable (D53).
 */
public abstract class DisplayBlock extends DeviceBlock implements EntityBlock {
    public static final BooleanProperty LIT = BlockStateProperties.LIT;
    private static final int LIGHT = 7; // D105

    protected DisplayBlock(Properties properties) {
        super(properties.lightLevel(state -> state.getValue(LIT) ? LIGHT : 0));
        registerDefaultState(defaultBlockState().setValue(LIT, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(LIT);
    }

    /** The anchor of the display that the space at {@code pos} belongs to. */
    public BlockPos anchor(BlockGetter level, BlockPos pos, BlockState state) {
        return pos;
    }

    /** The glass of the display anchored at {@code anchor}, or null if there's none to draw on. */
    @Nullable
    public abstract Screen screen(BlockGetter level, BlockPos anchor, BlockState state);

    /**
     * Where plug {@code i} (yellow, white, red, D103) of the AV Cable from a console at {@code console} goes in, in
     * world coordinates.
     */
    public abstract Vec3 jack(BlockGetter level, BlockPos anchor, BlockState state, int i, Vec3 console);

    /** Lights the display anchored at {@code anchor} up or down; true if anything changed. */
    protected boolean light(Level level, BlockPos anchor, BlockState state, boolean lit) {
        return setLitState(level, anchor, lit);
    }

    @Nullable
    public static DisplayBlock of(BlockState state) {
        return state.getBlock() instanceof DisplayBlock display ? display : null;
    }

    /** Lights the display anchored at {@code anchor} up or down, with its power sound (D105, D108, D109). */
    public static void setLit(Level level, BlockPos anchor, boolean lit) {
        BlockState state = level.getBlockState(anchor);
        DisplayBlock display = of(state);
        if (display != null && display.light(level, anchor, state, lit)) {
            CartridgeSounds.play(level, anchor, lit ? CartridgeSounds.TV_ON : CartridgeSounds.TV_OFF, 0.8F);
        }
    }

    /** Sets one space's LIT property; true if it changed. */
    protected static boolean setLitState(Level level, BlockPos pos, boolean lit) {
        BlockState state = level.getBlockState(pos);
        if (!state.hasProperty(LIT) || state.getValue(LIT) == lit) return false;
        level.setBlock(pos, state.setValue(LIT, lit), Block.UPDATE_ALL);
        return true;
    }

    /** Whether a display anchored here should be lit: it's linked to a console that's on. */
    protected static boolean shouldBeLit(Level level, BlockPos anchor) {
        return level.getBlockEntity(anchor) instanceof TvBlockEntity tv && tv.console() != null
                && level.getBlockEntity(tv.console()) instanceof ConsoleBlockEntity console && console.powered();
    }

    /** The glass of the display anchored at {@code anchor}, whatever kind it is; null if none. */
    @Nullable
    public static Screen screenAt(BlockGetter level, BlockPos anchor) {
        BlockState state = level.getBlockState(anchor);
        DisplayBlock display = of(state);
        return display == null ? null : display.screen(level, anchor, state);
    }

    /** Breaks the link at both ends; the caller hands out the cable (D53). */
    public static void unlink(Level level, BlockPos anchor, TvBlockEntity tv) {
        BlockPos console = tv.console();
        if (console == null) return;
        if (level.getBlockEntity(console) instanceof ConsoleBlockEntity consoleEntity) consoleEntity.removeTv(anchor);
        tv.setConsole(null);
    }

    /** A point in a block's north-facing model (pixels), in world coordinates, turned as its blockstate turns it. */
    public static Vec3 point(BlockPos pos, Direction facing, double xPx, double yPx, double zPx) {
        Matrix4f m = new Matrix4f().translate(pos.getX(), pos.getY(), pos.getZ()).translate(0.5F, 0, 0.5F)
                .rotateY((float) Math.toRadians(180 - facing.toYRot())).translate(-0.5F, 0, -0.5F);
        Vector3f v = m.transformPosition(new Vector3f((float) xPx / 16, (float) yPx / 16, (float) zPx / 16));
        return new Vec3(v.x, v.y, v.z);
    }

    /**
     * Glass on a north-facing model's front, x {@code x1..x2}, y {@code y1..y2}, at depth {@code z} (pixels). The
     * viewer, looking at the front, sees high x on their left.
     */
    protected static Screen glass(BlockPos pos, Direction facing, double x1, double y1, double x2, double y2, double z,
            Screen.Style style) {
        Vec3 origin = point(pos, facing, x2, y2, z);
        return new Screen(origin, point(pos, facing, x1, y2, z).subtract(origin),
                point(pos, facing, x2, y1, z).subtract(origin), style, null);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new TvBlockEntity(pos, state);
    }

    @Override
    protected Use itemUse(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
            InteractionHand hand, BlockHitResult hit) {
        return stack.is(CartridgeItems.AV_CABLE.get())
                ? Use.TO_ITEM
                : Use.EMPTY_HAND;
    }

    @Override
    protected Use emptyUse(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        BlockPos anchor = anchor(level, pos, state);
        if (!player.isSecondaryUseActive() || !(level.getBlockEntity(anchor) instanceof TvBlockEntity tv) || tv.console() == null) {
            return Use.PASS;
        }
        if (!level.isClientSide) {
            unlink(level, anchor, tv);
            ItemStack cable = new ItemStack(CartridgeItems.AV_CABLE.get());
            if (!player.getInventory().add(cable)) Compat.drop(player, cable);
            Compat.overlay(player, Component.translatable("cartridge_mod.tv.unlinked"));
        }
        return Use.SIDED;
    }

    @Override
    protected void removing(BlockState state, Level level, BlockPos pos) {
        if (level.getBlockEntity(pos) instanceof TvBlockEntity tv && tv.console() != null) {
            unlink(level, pos, tv);
            Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(), new ItemStack(CartridgeItems.AV_CABLE.get()));
        }
    }
}
