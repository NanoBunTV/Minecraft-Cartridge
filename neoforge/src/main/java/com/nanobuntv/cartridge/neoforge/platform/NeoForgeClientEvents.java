package com.nanobuntv.cartridge.neoforge.platform;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.world.item.Item;
import java.util.function.Function;
import com.nanobuntv.cartridge.client.CartridgeBlockEntityRenderer;
import com.nanobuntv.cartridge.client.ClientCompat;
import com.nanobuntv.cartridge.client.Draw;
//? if >=26.2 {
/*import com.nanobuntv.cartridge.client.RecordingBuffers;
*///?}
import com.nanobuntv.cartridge.platform.ClientEvents;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.MenuAccess;
//? if <26.2 {
import net.minecraft.client.renderer.MultiBufferSource;
//?}
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.common.NeoForge;

/**
 * Game-bus listeners are added right away; renderers and screens wait for their mod-bus events, which
 * {@link #attach(IEventBus)} hooks up from the client mod constructor.
 */
public final class NeoForgeClientEvents implements ClientEvents {
    private static final List<Consumer<EntityRenderersEvent.RegisterRenderers>> RENDERERS = new ArrayList<>();
    private static final List<Consumer<RegisterMenuScreensEvent>> SCREENS = new ArrayList<>();
    private static final List<Consumer<net.neoforged.neoforge.client.event.RegisterColorHandlersEvent.Block>> BLOCK_COLORS = new ArrayList<>();
    //? if <1.21.4 {
    private static final List<Consumer<net.neoforged.neoforge.client.event.RegisterColorHandlersEvent.Item>> ITEM_COLORS = new ArrayList<>();
    //?}


    @Override
    public void onTickEnd(Runnable listener) {
        NeoForge.EVENT_BUS.addListener((ClientTickEvent.Post event) -> listener.run());
    }

    @Override
    public void onHudRender(Consumer<GuiGraphics> listener) {
        NeoForge.EVENT_BUS.addListener((RenderGuiEvent.Post event) ->
                listener.accept(event.getGuiGraphics()));
    }

    @Override
    public void onRenderLevel(LevelRenderListener listener) {
        //? if >=26.2 {
        /*// No more buffer sources: what it draws is submitted with the frame's own.
        NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.client.event.SubmitCustomGeometryEvent event) -> {
            RecordingBuffers buffers = new RecordingBuffers(event.getSubmitNodeCollector());
            listener.render(event.getPoseStack(), buffers, event.getLevelRenderState().cameraRenderState.pos,
                    Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(false));
            buffers.submit();
        });
        *///?} else if >=1.21.9 {
        /*NeoForge.EVENT_BUS.addListener((RenderLevelStageEvent.AfterEntities event) -> {
            MultiBufferSource.BufferSource buffers = Minecraft.getInstance().renderBuffers().bufferSource();
            PoseStack pose = event.getPoseStack() != null ? event.getPoseStack() : new PoseStack();
            listener.render(pose, Draw.of(buffers), event.getLevelRenderState().cameraRenderState.pos,
                    Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(false));
            buffers.endLastBatch();
        });
        *///?} else {
        NeoForge.EVENT_BUS.addListener((RenderLevelStageEvent event) -> {
            if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES) return;
            MultiBufferSource.BufferSource buffers = Minecraft.getInstance().renderBuffers().bufferSource();
            listener.render(event.getPoseStack(), Draw.of(buffers), event.getCamera().getPosition(),
                    event.getPartialTick().getGameTimeDeltaPartialTick(false));
            buffers.endLastBatch();
        });
        //?}
    }

    @Override
    public void onDisconnect(Runnable listener) {
        NeoForge.EVENT_BUS.addListener((ClientPlayerNetworkEvent.LoggingOut event) -> listener.run());
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
        RENDERERS.add(event -> event.registerBlockEntityRenderer((BlockEntityType<T>) type.get(), provider));
    }

    @Override
    @SafeVarargs
    public final void registerBlockColor(BlockTint tint, Supplier<? extends net.minecraft.world.level.block.Block>... blocks) {
        BLOCK_COLORS.add(event -> {
            //? if >=26.1 {
            /*for (var block : blocks) event.register(ClientCompat.tintSources(tint), block.get());
            *///?} else {
            for (var block : blocks) event.register(tint::color, block.get());
            //?}
        });
    }

    @Override
    @SafeVarargs
    public final void registerItemColor(ItemTint tint, Supplier<? extends Item>... items) {
        //? if <1.21.4 {
        ITEM_COLORS.add(event -> {
            for (var item : items) event.register(tint::color, item.get());
        });
        //?}
    }

    @Override
    @SuppressWarnings("unchecked")
    public <M extends AbstractContainerMenu, S extends Screen & MenuAccess<M>> void registerMenuScreen(
            Supplier<? extends MenuType<? extends M>> type, ScreenFactory<M, S> factory) {
        SCREENS.add(event -> event.<M, S>register((MenuType<M>) type.get(), factory::create));
    }

    /** Called once from the client mod constructor. */
    public static void attach(IEventBus modBus) {
        modBus.addListener((EntityRenderersEvent.RegisterRenderers event) -> RENDERERS.forEach(r -> r.accept(event)));
        modBus.addListener((RegisterMenuScreensEvent event) -> SCREENS.forEach(r -> r.accept(event)));
        modBus.addListener((net.neoforged.neoforge.client.event.RegisterColorHandlersEvent.Block event) ->
                BLOCK_COLORS.forEach(r -> r.accept(event)));
        //? if <1.21.4 {
        modBus.addListener((net.neoforged.neoforge.client.event.RegisterColorHandlersEvent.Item event) ->
                ITEM_COLORS.forEach(r -> r.accept(event)));
        //?}
    }
}
