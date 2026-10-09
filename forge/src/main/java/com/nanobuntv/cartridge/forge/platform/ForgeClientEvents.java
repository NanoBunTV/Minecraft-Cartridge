package com.nanobuntv.cartridge.forge.platform;

import com.nanobuntv.cartridge.client.CartridgeBlockEntityRenderer;
import com.nanobuntv.cartridge.client.Draw;
import com.nanobuntv.cartridge.platform.ClientEvents;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.MenuAccess;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.RegisterColorHandlersEvent;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

/**
 * Game-bus listeners are added right away; renderers, tints and screens wait for their mod-bus events, which
 * {@link #attach(IEventBus)} hooks up from the client init. Menu screens are registered in client setup.
 */
public final class ForgeClientEvents implements ClientEvents {
    private static final List<Consumer<EntityRenderersEvent.RegisterRenderers>> RENDERERS = new ArrayList<>();
    private static final List<Consumer<RegisterColorHandlersEvent.Block>> BLOCK_COLORS = new ArrayList<>();
    private static final List<Consumer<RegisterColorHandlersEvent.Item>> ITEM_COLORS = new ArrayList<>();
    private static final List<Runnable> SCREENS = new ArrayList<>();

    @Override
    public void onTickEnd(Runnable listener) {
        MinecraftForge.EVENT_BUS.addListener((TickEvent.ClientTickEvent event) -> {
            if (event.phase == TickEvent.Phase.END) listener.run();
        });
    }

    @Override
    public void onHudRender(Consumer<GuiGraphics> listener) {
        //? if >=1.20 {
        MinecraftForge.EVENT_BUS.addListener((RenderGuiEvent.Post event) -> listener.accept(event.getGuiGraphics()));
        //?} else {
        /*MinecraftForge.EVENT_BUS.addListener((RenderGuiEvent.Post event) -> listener.accept(new GuiGraphics(event.getPoseStack())));
        *///?}
    }

    @Override
    public void onRenderLevel(LevelRenderListener listener) {
        MinecraftForge.EVENT_BUS.addListener((RenderLevelStageEvent event) -> {
            if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES) return;
            MultiBufferSource.BufferSource buffers = Minecraft.getInstance().renderBuffers().bufferSource();
            listener.render(event.getPoseStack(), Draw.of(buffers), event.getCamera().getPosition(), event.getPartialTick());
            buffers.endLastBatch();
        });
    }

    @Override
    public void onDisconnect(Runnable listener) {
        MinecraftForge.EVENT_BUS.addListener((ClientPlayerNetworkEvent.LoggingOut event) -> listener.run());
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T extends BlockEntity> void registerBlockEntityRenderer(Supplier<? extends BlockEntityType<? extends T>> type,
            Function<BlockEntityRendererProvider.Context, ? extends CartridgeBlockEntityRenderer<T>> factory) {
        BlockEntityRendererProvider<T> provider = factory::apply;
        RENDERERS.add(event -> event.registerBlockEntityRenderer((BlockEntityType<T>) type.get(), provider));
    }

    @Override
    @SafeVarargs
    public final void registerBlockColor(BlockTint tint, Supplier<? extends Block>... blocks) {
        BLOCK_COLORS.add(event -> {
            for (var block : blocks) event.register(tint::color, block.get());
        });
    }

    @Override
    @SafeVarargs
    public final void registerItemColor(ItemTint tint, Supplier<? extends Item>... items) {
        ITEM_COLORS.add(event -> {
            for (var item : items) event.register(tint::color, item.get());
        });
    }

    @Override
    @SuppressWarnings("unchecked")
    public <M extends AbstractContainerMenu, S extends Screen & MenuAccess<M>> void registerMenuScreen(
            Supplier<? extends MenuType<? extends M>> type, ScreenFactory<M, S> factory) {
        SCREENS.add(() -> MenuScreens.<M, S>register((MenuType<M>) type.get(), factory::create));
    }

    /** Called once from the client init. */
    public static void attach(IEventBus modBus) {
        modBus.addListener((EntityRenderersEvent.RegisterRenderers event) -> RENDERERS.forEach(r -> r.accept(event)));
        modBus.addListener((RegisterColorHandlersEvent.Block event) -> BLOCK_COLORS.forEach(r -> r.accept(event)));
        modBus.addListener((RegisterColorHandlersEvent.Item event) -> ITEM_COLORS.forEach(r -> r.accept(event)));
        modBus.addListener((FMLClientSetupEvent event) -> event.enqueueWork(() -> SCREENS.forEach(Runnable::run)));
    }
}
