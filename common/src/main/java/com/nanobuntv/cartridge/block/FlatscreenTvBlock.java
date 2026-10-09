package com.nanobuntv.cartridge.block;

import com.mojang.serialization.MapCodec;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The 1x1 Flatscreen TV (D129): a thin black LCD with 4:3 glass, on a desk stand, or hung flat against the block
 * behind it when placed on a block's side. Models: models/flatscreen_tv.bbmodel (stand) and flatscreen_tv_wall.bbmodel;
 * the power LED lights with the screen (D133).
 */
public class FlatscreenTvBlock extends DisplayBlock {
    //? if >=1.20.3 && <26.3 {
    public static final MapCodec<FlatscreenTvBlock> CODEC = simpleCodec(FlatscreenTvBlock::new);
    //?}
    public static final EnumProperty<DisplayMount> MOUNT = EnumProperty.create("mount", DisplayMount.class);

    private static final Map<Direction, VoxelShape> STAND = shapesByFacing(Shapes.or(
            box(0.5, 3, 7, 15.5, 15, 8),     // panel
            box(4, 5, 8, 12, 12, 9.5),       // back
            box(7, 1, 8.25, 9, 5, 9.25),     // neck
            box(3.5, 0, 5, 12.5, 1, 11)));   // foot
    private static final Map<Direction, VoxelShape> WALL = shapesByFacing(Shapes.or(
            box(0.5, 2, 14.4, 15.5, 14, 15.5),
            box(4, 4, 15.5, 12, 12, 16)));

    public FlatscreenTvBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(MOUNT, DisplayMount.STAND));
    }

    //? if >=1.20.3 && <26.3 {
    @Override
    protected MapCodec<FlatscreenTvBlock> codec() {
        return CODEC;
    }
    //?}

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(MOUNT);
    }

    /** D129: against a block's side it hangs on that block, facing away from it; otherwise it stands, facing the player. */
    static BlockState placed(BlockState state, BlockPlaceContext context) {
        Direction face = context.getClickedFace();
        if (face.getAxis().isHorizontal()) {
            return state.setValue(FACING, face).setValue(MOUNT, DisplayMount.WALL);
        }
        return state.setValue(FACING, context.getHorizontalDirection().getOpposite()).setValue(MOUNT, DisplayMount.STAND);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return placed(defaultBlockState(), context);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return (state.getValue(MOUNT) == DisplayMount.WALL ? WALL : STAND).get(state.getValue(FACING));
    }

    @Override
    public Screen screen(BlockGetter level, BlockPos anchor, BlockState state) {
        Direction facing = state.getValue(FACING);
        return state.getValue(MOUNT) == DisplayMount.WALL
                ? glass(anchor, facing, 1, 3, 15, 13.5, 14.35, Screen.Style.FLAT)
                : glass(anchor, facing, 1, 4, 15, 14.5, 6.85, Screen.Style.FLAT);
    }

    @Override
    public Vec3 jack(BlockGetter level, BlockPos anchor, BlockState state, int i, Vec3 console) {
        Direction facing = state.getValue(FACING);
        return state.getValue(MOUNT) == DisplayMount.WALL
                ? point(anchor, facing, 7 + i, 2, 15)      // under the panel
                : point(anchor, facing, 7 + i, 6, 9.5);    // on the back
    }
}
