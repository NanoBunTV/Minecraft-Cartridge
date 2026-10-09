package com.nanobuntv.cartridge.block;

import com.nanobuntv.cartridge.blockentity.TvBlockEntity;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * A video wall (D131): the Display Panels joined to one panel, side by side and facing the same way. It shows the
 * picture when they fill a rectangle of at most {@link #MAX_WIDTH} x {@link #MAX_HEIGHT}. Found by walking the
 * panels from any one of them; both sides work it out from the block states, so nothing about a wall is saved.
 *
 * @param panels    every panel in the wall (at most one more than the most a wall may have: the walk stops there)
 * @param topLeft   the top-left panel as the viewer sees it, of the wall's bounding rectangle
 * @param width     in panels
 * @param height    in panels
 * @param linked    the panels holding a cable link, the first being the wall's (D131: one per wall)
 */
public record DisplayWall(Direction facing, List<BlockPos> panels, BlockPos topLeft, int width, int height,
        List<BlockPos> linked) {
    public static final int MAX_WIDTH = 8, MAX_HEIGHT = 6;
    private static final int MAX_PANELS = MAX_WIDTH * MAX_HEIGHT;

    public enum Shape { OK, NOT_RECTANGLE, TOO_BIG }

    /** The viewer's right, along the wall. */
    public static Direction right(Direction facing) {
        return facing.getCounterClockWise();
    }

    /** The wall the panel at {@code start} belongs to, or null if there's no panel there. */
    @Nullable
    public static DisplayWall find(BlockGetter level, BlockPos start) {
        BlockState first = level.getBlockState(start);
        if (!(first.getBlock() instanceof DisplayPanelBlock)) return null;
        Direction facing = first.getValue(DisplayPanelBlock.FACING);
        Direction right = right(facing);
        Direction[] steps = {Direction.UP, Direction.DOWN, right, right.getOpposite()};
        Set<BlockPos> seen = new HashSet<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        seen.add(start.immutable());
        queue.add(start.immutable());
        while (!queue.isEmpty() && seen.size() <= MAX_PANELS) {
            BlockPos at = queue.poll();
            for (Direction step : steps) {
                BlockPos next = at.relative(step);
                if (!seen.contains(next) && DisplayPanelBlock.joins(level.getBlockState(next), facing)) {
                    seen.add(next);
                    queue.add(next);
                }
            }
        }
        // Columns count along the viewer's right; rows from the top.
        int minU = Integer.MAX_VALUE, maxU = Integer.MIN_VALUE, minY = Integer.MAX_VALUE, maxY = Integer.MIN_VALUE;
        for (BlockPos p : seen) {
            int u = along(p, right);
            minU = Math.min(minU, u);
            maxU = Math.max(maxU, u);
            minY = Math.min(minY, p.getY());
            maxY = Math.max(maxY, p.getY());
        }
        BlockPos topLeft = start.relative(right, minU - along(start, right)).atY(maxY);
        List<BlockPos> panels = new ArrayList<>(seen);
        panels.sort(Comparator.comparingLong(BlockPos::asLong)); // the same order on both sides
        List<BlockPos> linked = new ArrayList<>();
        for (BlockPos p : panels) {
            if (level.getBlockEntity(p) instanceof TvBlockEntity tv && tv.console() != null) linked.add(p);
        }
        return new DisplayWall(facing, panels, topLeft, maxU - minU + 1, maxY - minY + 1, linked);
    }

    private static int along(BlockPos p, Direction right) {
        return p.getX() * right.getStepX() + p.getZ() * right.getStepZ();
    }

    public Shape shape() {
        if (panels.size() > MAX_PANELS || width > MAX_WIDTH || height > MAX_HEIGHT) return Shape.TOO_BIG;
        return panels.size() == width * height ? Shape.OK : Shape.NOT_RECTANGLE;
    }

    /** The panel holding the wall's cable, or null. */
    @Nullable
    public BlockPos link() {
        return linked.isEmpty() ? null : linked.get(0);
    }
}
