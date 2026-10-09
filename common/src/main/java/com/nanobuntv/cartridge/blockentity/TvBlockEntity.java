package com.nanobuntv.cartridge.blockentity;

import com.nanobuntv.cartridge.block.DeviceBlock;
import com.nanobuntv.cartridge.block.DisplayBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;
//? if >=1.21.6 {
/*import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
*///?}

/**
 * A display's cable end (D18): which console feeds it. Every kind of display has one at its anchor (see
 * block/DisplayBlock); a video wall's panels each have one, and the linked one speaks for the wall (D131). The picture
 * is drawn by client/TvRenderer.
 */
public class TvBlockEntity extends BlockEntity {
    @Nullable
    private BlockPos console;

    public TvBlockEntity(BlockPos pos, BlockState state) {
        super(CartridgeBlockEntities.CRT_TV.get(), pos, state);
    }

    @Nullable
    public BlockPos console() {
        return console;
    }

    public void setConsole(@Nullable BlockPos console) {
        this.console = console == null ? null : console.immutable();
        setChanged();
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
            boolean lit = this.console != null && level.getBlockEntity(this.console) instanceof ConsoleBlockEntity c && c.powered();
            DisplayBlock.setLit(level, worldPosition, lit); // D105
        }
    }

    //? if >=1.21.6 {
    /*@Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        save(new Saved.Out(output));
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        load(new Saved.In(input));
    }
    *///?} else if >=1.20.5 {
    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        save(new Saved.Out(tag, registries));
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        load(new Saved.In(tag, registries));
    }
    //?} else {
    /*@Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        save(new Saved.Out(tag, null));
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        load(new Saved.In(tag, null));
    }
    *///?}

    //? if >=1.21.5 {
    /*// The block's removal side effects (dropping what's inside, unlinking), while this is still here (DeviceBlock).
    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        if (level != null && state.getBlock() instanceof DeviceBlock device) device.blockEntityRemoving(state, level, pos);
    }
    *///?}

    private void save(Saved.Out out) {
        if (console != null) out.putLong("console", console.asLong());
    }

    private void load(Saved.In in) {
        Long saved = in.getLong("console");
        console = saved != null ? BlockPos.of(saved) : null;
    }

    //? if >=1.20.5 {
    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return updateTag(registries);
    }
    //?} else {
    /*@Override
    public CompoundTag getUpdateTag() {
        return updateTag(null);
    }
    *///?}

    private CompoundTag updateTag(HolderLookup.Provider registries) {
        return Saved.customOnly(this, registries);
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
