package com.nanobuntv.cartridge.block;

import com.mojang.serialization.MapCodec;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/**
 * The Large Flatscreen TV (D129): 3 blocks wide and 2 tall, one item that fills six spaces. {@link #COLUMN} counts
 * from the viewer's left, {@link #ROW} from the bottom; the bottom middle space is the main one, where the player
 * clicked: it draws the whole model (the others have an empty one), holds the cable link and is the display's anchor.
 * Breaking any space removes the set. Models: models/large_flatscreen_tv.bbmodel and large_flatscreen_tv_wall.bbmodel.
 */
public class LargeFlatscreenTvBlock extends DisplayBlock {
    //? if >=1.20.3 && <26.3 {
    public static final MapCodec<LargeFlatscreenTvBlock> CODEC = simpleCodec(LargeFlatscreenTvBlock::new);
    //?}
    public static final IntegerProperty COLUMN = IntegerProperty.create("column", 0, 2);
    public static final IntegerProperty ROW = IntegerProperty.create("row", 0, 1);

    // The whole set in the main space's north-facing model, in pixels (it reaches one block either side, one up).
    private static final double[][] STAND_BOXES = {
            {-10.5, 3.5, 7, 26.5, 32, 8}, {-4, 8, 8, 20, 28, 9.5}, {6, 1, 8.25, 10, 6, 9.5}, {0, 0, 4, 16, 1, 12}};
    private static final double[][] WALL_BOXES = {
            {-10.5, 1.75, 14.4, 26.5, 30.25, 15.5}, {-4, 6, 15.5, 20, 26, 16}};
    /** [mount][column][row] -> shape per facing. */
    private static final Map<?, ?>[][][] SHAPES = new Map<?, ?>[2][3][2];

    static {
        for (DisplayMount mount : DisplayMount.values()) {
            for (int column = 0; column < 3; column++) {
                for (int row = 0; row < 2; row++) {
                    SHAPES[mount.ordinal()][column][row] = shapesByFacing(
                            partShape(mount == DisplayMount.WALL ? WALL_BOXES : STAND_BOXES, column, row));
                }
            }
        }
    }

    public LargeFlatscreenTvBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(FlatscreenTvBlock.MOUNT, DisplayMount.STAND)
                .setValue(COLUMN, 1).setValue(ROW, 0));
    }

    //? if >=1.20.3 && <26.3 {
    @Override
    protected MapCodec<LargeFlatscreenTvBlock> codec() {
        return CODEC;
    }
    //?}

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(FlatscreenTvBlock.MOUNT, COLUMN, ROW);
    }

    /** The part of the set's boxes inside one space; the viewer's left column is +x in the north-facing model. */
    private static VoxelShape partShape(double[][] boxes, int column, int row) {
        double dx = (1 - column) * 16, dy = row * 16;
        VoxelShape shape = Shapes.empty();
        for (double[] b : boxes) {
            double x1 = Math.max(0, b[0] - dx), x2 = Math.min(16, b[3] - dx);
            double y1 = Math.max(0, b[1] - dy), y2 = Math.min(16, b[4] - dy);
            if (x1 < x2 && y1 < y2) shape = Shapes.or(shape, box(x1, y1, b[2], x2, y2, b[5]));
        }
        return shape;
    }

    /** Where the space at {@code column}, {@code row} of the set whose main space is {@code main} lies. */
    private static BlockPos part(BlockPos main, Direction facing, int column, int row) {
        return main.relative(facing.getCounterClockWise(), column - 1).above(row); // counter-clockwise: viewer's right
    }

    private static BlockPos main(BlockPos pos, BlockState state) {
        return part(pos, state.getValue(FACING), 2 - state.getValue(COLUMN), -state.getValue(ROW));
    }

    @Override
    public BlockPos anchor(BlockGetter level, BlockPos pos, BlockState state) {
        return main(pos, state);
    }

    @Override
    @Nullable
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockState state = FlatscreenTvBlock.placed(defaultBlockState(), context);
        Level level = context.getLevel();
        Direction facing = state.getValue(FACING);
        for (int column = 0; column < 3; column++) {
            for (int row = 0; row < 2; row++) {
                BlockPos p = part(context.getClickedPos(), facing, column, row);
                if (!level.isInWorldBounds(p) || !level.getBlockState(p).canBeReplaced(context)) return null;
            }
        }
        return state;
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        Direction facing = state.getValue(FACING);
        for (int column = 0; column < 3; column++) {
            for (int row = 0; row < 2; row++) {
                if (column == 1 && row == 0) continue;
                level.setBlock(part(pos, facing, column, row), state.setValue(COLUMN, column).setValue(ROW, row), Block.UPDATE_ALL);
            }
        }
    }

    @Override
    protected void removed(BlockState state, Level level, BlockPos pos) {
        // The rest of the set goes too (only the broken space drops the item).
        BlockPos main = main(pos, state);
        Direction facing = state.getValue(FACING);
        for (int column = 0; column < 3; column++) {
            for (int row = 0; row < 2; row++) {
                BlockPos p = part(main, facing, column, row);
                if (!p.equals(pos) && level.getBlockState(p).is(this)) {
                    level.setBlock(p, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
                }
            }
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        Map<Direction, VoxelShape> shapes = (Map<Direction, VoxelShape>)
                SHAPES[state.getValue(FlatscreenTvBlock.MOUNT).ordinal()][state.getValue(COLUMN)][state.getValue(ROW)];
        return shapes.get(state.getValue(FACING));
    }

    @Override
    @Nullable
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return state.getValue(COLUMN) == 1 && state.getValue(ROW) == 0 ? super.newBlockEntity(pos, state) : null;
    }

    @Override
    protected boolean light(Level level, BlockPos anchor, BlockState state, boolean lit) {
        Direction facing = state.getValue(FACING);
        boolean changed = false;
        for (int column = 0; column < 3; column++) {
            for (int row = 0; row < 2; row++) {
                BlockPos p = part(anchor, facing, column, row);
                if (level.getBlockState(p).is(this)) changed |= setLitState(level, p, lit);
            }
        }
        return changed;
    }

    @Override
    public Screen screen(BlockGetter level, BlockPos anchor, BlockState state) {
        Direction facing = state.getValue(FACING);
        return state.getValue(FlatscreenTvBlock.MOUNT) == DisplayMount.WALL
                ? glass(anchor, facing, -10, 2.75, 26, 29.75, 14.35, Screen.Style.FLAT)
                : glass(anchor, facing, -10, 4.5, 26, 31.5, 6.85, Screen.Style.FLAT);
    }

    @Override
    public Vec3 jack(BlockGetter level, BlockPos anchor, BlockState state, int i, Vec3 console) {
        Direction facing = state.getValue(FACING);
        return state.getValue(FlatscreenTvBlock.MOUNT) == DisplayMount.WALL
                ? point(anchor, facing, 7 + i, 1.75, 15)
                : point(anchor, facing, 7 + i, 9, 9.5);
    }

    //? if <1.20 {
    /*@Override
    public net.minecraft.world.level.material.PushReaction getPistonPushReaction(BlockState state) {
        return net.minecraft.world.level.material.PushReaction.BLOCK; // from 1.20 a block property (CartridgeBlocks)
    }
    *///?}
}
