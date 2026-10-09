package com.nanobuntv.cartridge.block;

import com.mojang.serialization.MapCodec;
import com.nanobuntv.cartridge.blockentity.CartridgeWriterBlockEntity;
import com.nanobuntv.cartridge.util.Use;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The Cartridge Writer, styled as an EPROM burner (D15, D45). Model: models/cartridge_writer.bbmodel. The cart goes
 * into the {@code socket} element on top (x 3..11, z 4..10, slot along x); {@code status_led} is on the front.
 * Right-clicking opens its GUI (menu/WriterMenu).
 */
public class CartridgeWriterBlock extends DeviceBlock implements EntityBlock {
    //? if >=1.20.3 && <26.3 {
    public static final MapCodec<CartridgeWriterBlock> CODEC = simpleCodec(CartridgeWriterBlock::new);
    //?}

    // Body, and the socket with its lever (the ribbon cable at the back is left out).
    private static final Map<Direction, VoxelShape> SHAPES = shapesByFacing(Shapes.or(
            box(1, 0, 2, 15, 6, 14),
            box(1.75, 6, 3, 11, 7, 10)));

    public CartridgeWriterBlock(Properties properties) {
        super(properties);
    }

    //? if >=1.20.3 && <26.3 {
    @Override
    protected MapCodec<CartridgeWriterBlock> codec() {
        return CODEC;
    }
    //?}

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPES.get(state.getValue(FACING));
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new CartridgeWriterBlockEntity(pos, state);
    }

    @Override
    protected Use emptyUse(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!(level.getBlockEntity(pos) instanceof CartridgeWriterBlockEntity writer)) return Use.PASS;
        if (!level.isClientSide) player.openMenu(writer);
        return Use.SIDED;
    }

    @Override
    protected void removing(BlockState state, Level level, BlockPos pos) {
        if (level.getBlockEntity(pos) instanceof CartridgeWriterBlockEntity writer) writer.drop(level, pos);
    }
}
