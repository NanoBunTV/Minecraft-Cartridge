package com.nanobuntv.cartridge.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jetbrains.annotations.Nullable;
//? if <1.21.9 {
import net.minecraft.client.renderer.MultiBufferSource;
//?}
//? if >=1.21.9 {
/*import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.state.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.phys.Vec3;
*///?}

/**
 * The mod's block entity renderers (and an add-on's), each a {@link #draw} into a {@link Draw} on every Minecraft
 * version (Porting.md). Up to 1.21.8 Minecraft's own {@code render} hands it its buffer source. From 1.21.9 Minecraft
 * extracts a render state and later submits it: the state here carries the block entity itself (both happen on the
 * render thread in the same frame), and {@link RecordingBuffers} hands what {@link #draw} draws to the collector.
 */
//? if >=1.21.9 {
/*public abstract class CartridgeBlockEntityRenderer<T extends BlockEntity>
        implements BlockEntityRenderer<T, CartridgeBlockEntityRenderer.State<T>> {
    public static final class State<T extends BlockEntity> extends BlockEntityRenderState {
        @Nullable T blockEntity;
        float partialTick;
    }

    @Override
    public State<T> createRenderState() {
        return new State<>();
    }

    @Override
    public void extractRenderState(T blockEntity, State<T> state, float partialTick, Vec3 camera,
            ModelFeatureRenderer.@Nullable CrumblingOverlay breaking) {
        BlockEntityRenderer.super.extractRenderState(blockEntity, state, partialTick, camera, breaking);
        state.blockEntity = blockEntity;
        state.partialTick = partialTick;
    }

    @Override
    public void submit(State<T> state, PoseStack pose, SubmitNodeCollector collector, CameraRenderState camera) {
        T blockEntity = state.blockEntity;
        state.blockEntity = null;
        if (blockEntity == null) return;
        RecordingBuffers buffers = new RecordingBuffers(collector);
        draw(blockEntity, state.partialTick, pose, buffers, state.lightCoords, OverlayTexture.NO_OVERLAY);
        buffers.submit();
    }

    @Override
    public boolean shouldRenderOffScreen() {
        return drawsOffScreen(null);
    }

*///?} else {
public abstract class CartridgeBlockEntityRenderer<T extends BlockEntity> implements BlockEntityRenderer<T> {
    @Override
    public final void render(T blockEntity, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        draw(blockEntity, partialTick, pose, Draw.of(buffers), light, overlay);
    }

    @Override
    public boolean shouldRenderOffScreen(T blockEntity) {
        return drawsOffScreen(blockEntity);
    }
//?}

    /** Draws the block entity, the pose at its block's corner. */
    public abstract void draw(T blockEntity, float partialTick, PoseStack pose, Draw buffers, int light, int overlay);

    /**
     * Whether it draws past its block (cables, a big glass), so it's drawn even when the block is off screen. Null:
     * any of them (from 1.21.9 Minecraft asks once per renderer).
     */
    protected boolean drawsOffScreen(@Nullable T blockEntity) {
        return false;
    }
}
