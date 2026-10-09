package com.nanobuntv.cartridge.block;

import com.nanobuntv.cartridge.util.Use;
import java.util.EnumMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
//? if >=1.20.5 && <1.21.2 {
import net.minecraft.world.ItemInteractionResult;
//?}
//? if >=1.21.5 {
/*import net.minecraft.server.level.ServerLevel;
*///?}

/**
 * A device whose front faces the player who placed it: the TV, the console and the Cartridge Writer. Clicks and
 * removal go through version-neutral hooks ({@link #itemUse}, {@link #emptyUse}, {@link #removing}, {@link #removed});
 * only this class has the overrides each Minecraft version calls (Porting.md).
 */
public abstract class DeviceBlock extends HorizontalDirectionalBlock {
    protected DeviceBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    /** Right-clicked holding {@code stack} (perhaps empty). Default: go on as an empty-handed click. */
    protected Use itemUse(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand,
            BlockHitResult hit) {
        return Use.EMPTY_HAND;
    }

    /** Right-clicked with the main hand, after {@link #itemUse} left it to this. */
    protected Use emptyUse(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        return Use.PASS;
    }

    /** Server: the block here is going (broken, replaced, blown up), its block entity still here. */
    protected void removing(BlockState state, Level level, BlockPos pos) {}

    /** {@link #removing}, for the block entities: from 1.21.5 they report it (preRemoveSideEffects). */
    public final void blockEntityRemoving(BlockState state, Level level, BlockPos pos) {
        removing(state, level, pos);
    }

    /** Server: the block here has gone; for neighbours and the other spaces of a device filling several. */
    protected void removed(BlockState state, Level level, BlockPos pos) {}

    //? if >=1.21.2 {
    /*@Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
            InteractionHand hand, BlockHitResult hit) {
        return itemUse(stack, state, level, pos, player, hand, hit).itemOn(level);
    }
    *///?} else if >=1.20.5 {
    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
            InteractionHand hand, BlockHitResult hit) {
        return itemUse(stack, state, level, pos, player, hand, hit).itemOn(level);
    }
    //?}

    //? if >=1.20.5 {
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        return emptyUse(state, level, pos, player, hit).result(level);
    }
    //?} else {
    /*@Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand,
            BlockHitResult hit) {
        return Use.legacy(itemUse(player.getItemInHand(hand), state, level, pos, player, hand, hit), hand, level,
                () -> emptyUse(state, level, pos, player, hit));
    }
    *///?}

    //? if >=1.21.5 {
    /*// The block entities call removing() from preRemoveSideEffects, while they're still there.
    @Override
    protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel level, BlockPos pos, boolean movedByPiston) {
        super.affectNeighborsAfterRemoval(state, level, pos, movedByPiston);
        removed(state, level, pos);
    }
    *///?} else {
    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        boolean gone = !state.is(newState.getBlock());
        if (gone) removing(state, level, pos);
        super.onRemove(state, level, pos, newState, movedByPiston);
        if (gone) removed(state, level, pos);
    }
    //?}

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    /**
     * Turns a shape drawn facing north (as in Blockbench) into one shape per facing, matching the blockstate's
     * y rotations (east = 90 degrees clockwise seen from above).
     */
    protected static Map<Direction, VoxelShape> shapesByFacing(VoxelShape north) {
        Map<Direction, VoxelShape> shapes = new EnumMap<>(Direction.class);
        for (Direction facing : Direction.Plane.HORIZONTAL) {
            VoxelShape[] rotated = {Shapes.empty()};
            north.forAllBoxes((x1, y1, z1, x2, y2, z2) -> {
                VoxelShape box = switch (facing) {
                    case EAST -> Shapes.box(1 - z2, y1, x1, 1 - z1, y2, x2);
                    case SOUTH -> Shapes.box(1 - x2, y1, 1 - z2, 1 - x1, y2, 1 - z1);
                    case WEST -> Shapes.box(z1, y1, 1 - x2, z2, y2, 1 - x1);
                    default -> Shapes.box(x1, y1, z1, x2, y2, z2);
                };
                rotated[0] = Shapes.or(rotated[0], box);
            });
            shapes.put(facing, rotated[0].optimize());
        }
        return shapes;
    }
}
