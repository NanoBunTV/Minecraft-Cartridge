package com.nanobuntv.cartridge.arcade.block;

import com.nanobuntv.cartridge.blockentity.Saved;
import com.nanobuntv.cartridge.arcade.ArcadeContent;
import com.nanobuntv.cartridge.blockentity.ConsoleBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/** An Arcade Cabinet's console (D198), in its anchor space, with its body's colour (D204). */
public class ArcadeCabinetBlockEntity extends ConsoleBlockEntity {
    private int color = ArcadeCabinetBlock.DEFAULT_COLOR;

    public ArcadeCabinetBlockEntity(BlockPos pos, BlockState state) {
        super(ArcadeContent.CABINET_ENTITY.get(), pos, state);
    }

    public int color() {
        return color;
    }

    public void setColor(int color) {
        this.color = color & 0xFFFFFF;
        setChanged();
    }

    @Override
    protected void save(Saved.Out out) {
        super.save(out);
        out.putInt("color", color);
    }

    @Override
    protected void load(Saved.In in) {
        super.load(in);
        int was = color;
        color = in.getInt("color", ArcadeCabinetBlock.DEFAULT_COLOR);
        if (was != color && level != null && level.isClientSide) {
            // The tint is baked into the chunk's mesh: draw the cabinet again in its colour.
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_IMMEDIATE);
        }
    }
}
