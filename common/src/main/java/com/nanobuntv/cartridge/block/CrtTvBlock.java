package com.nanobuntv.cartridge.block;

import com.mojang.serialization.MapCodec;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The 1x1 CRT TV (D17, D40), a grey/beige portable set. Model: models/crt_tv.bbmodel. The screen is the model's
 * {@code screen} element: x 4..14, y 3..11 on the front (north) face, 0.75 px deep; client/TvRenderer draws there.
 */
public class CrtTvBlock extends DisplayBlock {
    //? if >=1.20.3 && <26.3 {
    public static final MapCodec<CrtTvBlock> CODEC = simpleCodec(CrtTvBlock::new);
    //?}

    // Cabinet with bezel, the CRT's rear bulge, and the carry handle (the thin antenna is left out).
    private static final Map<Direction, VoxelShape> SHAPES = shapesByFacing(Shapes.or(
            box(1, 0, 0, 15, 13, 10),
            box(3, 2, 10, 13, 12, 15),
            box(3, 13, 4, 13, 16, 6)));

    public CrtTvBlock(Properties properties) {
        super(properties);
    }

    //? if >=1.20.3 && <26.3 {
    @Override
    protected MapCodec<CrtTvBlock> codec() {
        return CODEC;
    }
    //?}

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPES.get(state.getValue(FACING));
    }

    @Override
    public Screen screen(BlockGetter level, BlockPos anchor, BlockState state) {
        return glass(anchor, state.getValue(FACING), 4, 3, 14, 11, 0.7, Screen.Style.CRT);
    }

    @Override
    public Vec3 jack(BlockGetter level, BlockPos anchor, BlockState state, int i, Vec3 console) {
        return point(anchor, state.getValue(FACING), 7 + i, 4, 15); // on the rear bulge
    }
}
