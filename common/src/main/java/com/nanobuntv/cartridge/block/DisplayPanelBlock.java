package com.nanobuntv.cartridge.block;

import com.mojang.serialization.MapCodec;
import com.nanobuntv.cartridge.blockentity.TvBlockEntity;
import com.nanobuntv.cartridge.item.CartridgeItems;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.Containers;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
//? if >=1.21.2 {
/*import net.minecraft.util.RandomSource;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
*///?}
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A Display Panel (D131): a thin screen tile at the back of its space. Panels side by side facing the same way join
 * into a video wall ({@link DisplayWall}); {@link #TOP}, {@link #BOTTOM}, {@link #LEFT} and {@link #RIGHT} (the
 * viewer's) say which sides have a panel beside them, so the bezel shows only round the outside (D132). Every panel
 * has a {@link TvBlockEntity}; the one the cable went into is the wall's anchor, and it alone draws the picture across
 * the wall. Model: models/display_panel.bbmodel, split into glass and edge pieces.
 */
public class DisplayPanelBlock extends DisplayBlock {
    //? if >=1.20.3 && <26.3 {
    public static final MapCodec<DisplayPanelBlock> CODEC = simpleCodec(DisplayPanelBlock::new);
    //?}
    public static final BooleanProperty TOP = BooleanProperty.create("top");
    public static final BooleanProperty BOTTOM = BooleanProperty.create("bottom");
    public static final BooleanProperty LEFT = BooleanProperty.create("left");
    public static final BooleanProperty RIGHT = BooleanProperty.create("right");
    private static final Map<Direction, VoxelShape> SHAPES = shapesByFacing(box(0, 0, 14.25, 16, 16, 16));

    public DisplayPanelBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(TOP, false).setValue(BOTTOM, false).setValue(LEFT, false)
                .setValue(RIGHT, false));
    }

    //? if >=1.20.3 && <26.3 {
    @Override
    protected MapCodec<DisplayPanelBlock> codec() {
        return CODEC;
    }
    //?}

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(TOP, BOTTOM, LEFT, RIGHT);
    }

    /** Whether {@code state} is a panel that joins a wall facing {@code facing}. */
    static boolean joins(BlockState state, Direction facing) {
        return state.getBlock() instanceof DisplayPanelBlock && state.getValue(FACING) == facing;
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction face = context.getClickedFace();
        BlockState against = context.getLevel().getBlockState(context.getClickedPos().relative(face.getOpposite()));
        Direction facing;
        if (against.getBlock() instanceof DisplayPanelBlock) {
            facing = against.getValue(FACING); // building on a wall: the same way as it
        } else if (face.getAxis().isHorizontal()) {
            facing = face; // on a block's side, against it
        } else {
            facing = context.getHorizontalDirection().getOpposite();
        }
        return connected(defaultBlockState().setValue(FACING, facing), context.getLevel(), context.getClickedPos());
    }

    private static BlockState connected(BlockState state, BlockGetter level, BlockPos pos) {
        Direction facing = state.getValue(FACING), right = DisplayWall.right(facing);
        return state.setValue(TOP, joins(level.getBlockState(pos.above()), facing))
                .setValue(BOTTOM, joins(level.getBlockState(pos.below()), facing))
                .setValue(RIGHT, joins(level.getBlockState(pos.relative(right)), facing))
                .setValue(LEFT, joins(level.getBlockState(pos.relative(right.getOpposite())), facing));
    }

    //? if >=1.21.2 {
    /*@Override
    public BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos,
            Direction direction, BlockPos neighborPos, BlockState neighbor, RandomSource random) {
        return joined(state, direction, neighbor);
    }
    *///?} else {
    @Override
    public BlockState updateShape(BlockState state, Direction direction, BlockState neighbor, LevelAccessor level,
            BlockPos pos, BlockPos neighborPos) {
        return joined(state, direction, neighbor);
    }
    //?}

    /** Its edges join the neighbouring panels of the same wall. */
    private static BlockState joined(BlockState state, Direction direction, BlockState neighbor) {
        Direction right = DisplayWall.right(state.getValue(FACING));
        boolean joins = joins(neighbor, state.getValue(FACING));
        if (direction == Direction.UP) return state.setValue(TOP, joins);
        if (direction == Direction.DOWN) return state.setValue(BOTTOM, joins);
        if (direction == right) return state.setValue(RIGHT, joins);
        if (direction == right.getOpposite()) return state.setValue(LEFT, joins);
        return state;
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPES.get(state.getValue(FACING));
    }

    /** The wall's linked panel, or this one if the wall has no cable yet. */
    @Override
    public BlockPos anchor(BlockGetter level, BlockPos pos, BlockState state) {
        DisplayWall wall = DisplayWall.find(level, pos);
        BlockPos link = wall == null ? null : wall.link();
        return link != null ? link : pos;
    }

    @Override
    public Screen screen(BlockGetter level, BlockPos anchor, BlockState state) {
        DisplayWall wall = DisplayWall.find(level, anchor);
        if (wall == null) return null;
        Direction facing = state.getValue(FACING);
        DisplayWall.Shape shape = wall.shape();
        if (shape != DisplayWall.Shape.OK) {
            // Shown on the linked panel alone (D131).
            Screen own = glass(anchor, facing, 0.5, 0.5, 15.5, 15.5, 14.45, Screen.Style.FLAT);
            Component why = shape == DisplayWall.Shape.TOO_BIG
                    ? Component.translatable("cartridge_mod.wall.too_big", DisplayWall.MAX_WIDTH, DisplayWall.MAX_HEIGHT)
                    : Component.translatable("cartridge_mod.wall.not_rectangle");
            return new Screen(own.origin(), own.right(), own.down(), own.style(), why);
        }
        // The glass inside the bezel round the outside: half a pixel in from each edge.
        Vec3 origin = point(wall.topLeft(), facing, 15.5, 15.5, 14.45);
        Direction right = DisplayWall.right(facing);
        Vec3 along = new Vec3(right.getStepX(), 0, right.getStepZ()).scale(wall.width() - 1 / 16.0);
        Vec3 down = new Vec3(0, -(wall.height() - 1 / 16.0), 0);
        return new Screen(origin, along, down, Screen.Style.FLAT, null);
    }

    /** Under the wall: the bottom edge of the bottom-row panel nearest the console, whichever panel holds the link. */
    @Override
    public Vec3 jack(BlockGetter level, BlockPos anchor, BlockState state, int i, Vec3 console) {
        DisplayWall wall = DisplayWall.find(level, anchor);
        BlockPos at = anchor;
        if (wall != null) {
            int bottom = wall.panels().stream().mapToInt(BlockPos::getY).min().orElse(anchor.getY());
            double best = Double.MAX_VALUE;
            for (BlockPos p : wall.panels()) {
                double d = Vec3.atCenterOf(p).distanceToSqr(console);
                if (p.getY() == bottom && d < best) {
                    best = d;
                    at = p;
                }
            }
        }
        return point(at, state.getValue(FACING), 7 + i, 0.25, 15.5);
    }

    /** The whole wall lights with its console; only its linked panel speaks for it. */
    @Override
    protected boolean light(Level level, BlockPos anchor, BlockState state, boolean lit) {
        DisplayWall wall = DisplayWall.find(level, anchor);
        if (wall == null || wall.link() != null && !wall.link().equals(anchor)) return false;
        boolean changed = false;
        for (BlockPos p : wall.panels()) changed |= setLitState(level, p, lit);
        return changed;
    }

    @Override
    public void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        if (!level.isClientSide && !oldState.is(this)) refresh(level, pos);
    }

    @Override
    protected void removed(BlockState state, Level level, BlockPos pos) {
        if (!level.isClientSide) {
            Direction right = DisplayWall.right(state.getValue(FACING));
            for (Direction side : List.of(Direction.UP, Direction.DOWN, right, right.getOpposite())) {
                if (joins(level.getBlockState(pos.relative(side)), state.getValue(FACING))) refresh(level, pos.relative(side));
            }
        }
    }

    /**
     * After the wall changed shape: one cable per wall (D131), so when walls join, every cable but one pops off; then
     * the whole wall lights to match its console.
     */
    private static void refresh(Level level, BlockPos pos) {
        DisplayWall wall = DisplayWall.find(level, pos);
        if (wall == null) return;
        for (BlockPos extra : wall.linked().subList(Math.min(1, wall.linked().size()), wall.linked().size())) {
            if (level.getBlockEntity(extra) instanceof TvBlockEntity tv) {
                unlink(level, extra, tv);
                Containers.dropItemStack(level, extra.getX(), extra.getY(), extra.getZ(), new ItemStack(CartridgeItems.AV_CABLE.get()));
            }
        }
        BlockPos link = wall.link();
        boolean lit = link != null && shouldBeLit(level, link);
        for (BlockPos p : wall.panels()) setLitState(level, p, lit);
    }
}
