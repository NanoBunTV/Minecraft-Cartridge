package com.nanobuntv.cartridge.fabric.platform;

import net.minecraft.world.item.Item;
import java.util.function.Consumer;
import java.util.function.Function;
import com.nanobuntv.cartridge.client.CartridgeBlockEntityRenderer;
import com.nanobuntv.cartridge.client.ClientCompat;
import com.nanobuntv.cartridge.client.Draw;
//? if >=26.2 {
/*import com.nanobuntv.cartridge.client.RecordingBuffers;
*///?}
import com.nanobuntv.cartridge.platform.ClientEvents;
import java.util.function.Supplier;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
//? if <26.1 {
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
//?}
//? if >=1.21.9 && <26.1 {
/*import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
*///?} else if <1.21.9 {
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
//?}
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.MenuAccess;
//? if <26.2 {
import net.minecraft.client.renderer.MultiBufferSource;
//?}
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderers;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;

/** Fabric registers everything immediately: the client initializer runs after the registries are filled. */
public final class FabricClientEvents implements ClientEvents {

    @Override
    public void onTickEnd(Runnable listener) {
        ClientTickEvents.END_CLIENT_TICK.register(client -> listener.run());
    }

    @Override
    public void onHudRender(Consumer<GuiGraphics> listener) {
        //? if >=26.1 {
        /*net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry.addLast(
                net.minecraft.resources.Identifier.fromNamespaceAndPath("cartridge_mod", "play_mode"),
                (graphics, delta) -> listener.accept(graphics));
        *///?} else if >=1.20 {
        HudRenderCallback.EVENT.register((graphics, delta) -> listener.accept(graphics));
        //?} else {
        /*HudRenderCallback.EVENT.register((pose, delta) -> listener.accept(new GuiGraphics(pose)));
        *///?}
    }

    @Override
    public void onRenderLevel(LevelRenderListener listener) {
        //? if >=26.2 {
        /*// No more buffer sources: what it draws is submitted with the frame's own.
        net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents.COLLECT_SUBMITS.register(context -> {
            RecordingBuffers buffers = new RecordingBuffers(context.submitNodeCollector());
            listener.render(context.poseStack(), buffers, context.levelState().cameraRenderState.pos,
                    Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(false));
            buffers.submit();
        });
        *///?} else if >=26.1 {
        /*net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents.AFTER_SOLID_FEATURES.register(context -> {
            MultiBufferSource.BufferSource buffers = context.bufferSource();
            listener.render(context.poseStack(), Draw.of(buffers), context.levelState().cameraRenderState.pos,
                    Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(false));
            buffers.endLastBatch();
        });
        *///?} else if >=1.21.9 {
        /*WorldRenderEvents.AFTER_ENTITIES.register(context -> {
            MultiBufferSource.BufferSource buffers = Minecraft.getInstance().renderBuffers().bufferSource();
            listener.render(context.matrices(), Draw.of(buffers), context.worldState().cameraRenderState.pos,
                    Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(false));
            buffers.endLastBatch();
        });
        *///?} else if >=1.21 {
        WorldRenderEvents.AFTER_ENTITIES.register(context -> {
            MultiBufferSource.BufferSource buffers = Minecraft.getInstance().renderBuffers().bufferSource();
            listener.render(context.matrixStack(), Draw.of(buffers), context.camera().getPosition(),
                    context.tickCounter().getGameTimeDeltaPartialTick(false));
            buffers.endLastBatch();
        });
        //?} else {
        /*WorldRenderEvents.AFTER_ENTITIES.register(context -> {
            MultiBufferSource.BufferSource buffers = Minecraft.getInstance().renderBuffers().bufferSource();
            listener.render(context.matrixStack(), Draw.of(buffers), context.camera().getPosition(), context.tickDelta());
            buffers.endLastBatch();
        });
        *///?}
    }

    @Override
    public void onDisconnect(Runnable listener) {
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> listener.run());
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T extends BlockEntity> void registerBlockEntityRenderer(Supplier<? extends BlockEntityType<? extends T>> type,
            Function<BlockEntityRendererProvider.Context, ? extends CartridgeBlockEntityRenderer<T>> factory) {
        //? if >=1.21.9 {
        /*BlockEntityRendererProvider<T, CartridgeBlockEntityRenderer.State<T>> provider = factory::apply;
        *///?} else {
        BlockEntityRendererProvider<T> provider = factory::apply;
        //?}
        BlockEntityRenderers.register((BlockEntityType<T>) type.get(), provider);
    }

    @Override
    @SafeVarargs
    public final void registerBlockColor(BlockTint tint, Supplier<? extends net.minecraft.world.level.block.Block>... blocks) {
        for (var block : blocks) {
            //? if >=26.1 {
            /*net.fabricmc.fabric.api.client.rendering.v1.BlockColorRegistry.register(ClientCompat.tintSources(tint), block.get());
            *///?} else {
            net.fabricmc.fabric.api.client.rendering.v1.ColorProviderRegistry.BLOCK.register(tint::color, block.get());
            //?}
        }
    }

    @Override
    @SafeVarargs
    public final void registerItemColor(ItemTint tint, Supplier<? extends Item>... items) {
        //? if <1.21.4 {
        for (var item : items) net.fabricmc.fabric.api.client.rendering.v1.ColorProviderRegistry.ITEM.register(tint::color, item.get());
        //?}
    }

    @Override
    @SuppressWarnings("unchecked")
    public <M extends AbstractContainerMenu, S extends Screen & MenuAccess<M>> void registerMenuScreen(
            Supplier<? extends MenuType<? extends M>> type, ScreenFactory<M, S> factory) {
        MenuScreens.<M, S>register((MenuType<M>) type.get(), factory::create);
    }
}
