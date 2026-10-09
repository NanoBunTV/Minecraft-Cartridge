package com.nanobuntv.cartridge.platform;

import com.mojang.blaze3d.vertex.PoseStack;
import com.nanobuntv.cartridge.client.CartridgeBlockEntityRenderer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import net.minecraft.client.gui.GuiGraphics;
import com.nanobuntv.cartridge.client.Draw;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.MenuAccess;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.Item;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.phys.Vec3;

/**
 * Client-side hooks (D32). Loaded only on the client, through {@link ClientServices}. Registry objects are passed as
 * suppliers because NeoForge creates them after mod construction; registrations that need them run at the loader's
 * matching event.
 */
public interface ClientEvents {

    void onTickEnd(Runnable listener);

    /** After the vanilla HUD, every frame it's drawn. */
    void onHudRender(Consumer<GuiGraphics> listener);

    /** Leaving a world or server. */
    void onDisconnect(Runnable listener);

    /** Something drawn in the world after the entities: the pose is the camera's, positions are less the camera's. */
    interface LevelRenderListener {
        void render(PoseStack pose, Draw buffers, Vec3 camera, float partialTick);
    }

    void onRenderLevel(LevelRenderListener listener);

    /** A block entity renderer (its type changed with every rendering rework: the mod's own base class hides that). */
    <T extends BlockEntity> void registerBlockEntityRenderer(Supplier<? extends BlockEntityType<? extends T>> type,
            Function<BlockEntityRendererProvider.Context, ? extends CartridgeBlockEntityRenderer<T>> factory);

    /** A tint for blocks' models (an arcade cabinet's dyed body, D204). From 26.1 only tint index 0 is used. */
    @SuppressWarnings("unchecked")
    void registerBlockColor(BlockTint tint, Supplier<? extends Block>... blocks);

    /** A block's tint per model tint index, {@code 0xAARRGGBB}; -1 for none. No level or position: in no world. */
    @FunctionalInterface
    interface BlockTint {
        int color(BlockState state, @Nullable BlockAndTintGetter level, @Nullable BlockPos pos, int tintIndex);
    }

    /**
     * A tint for items' models. From 1.21.4 the items' model definitions ({@code assets/<mod>/items/}) carry their tints
     * instead, and this does nothing.
     */
    @SuppressWarnings("unchecked")
    void registerItemColor(ItemTint tint, Supplier<? extends Item>... items);

    /** An item's tint per model tint index, {@code 0xAARRGGBB}; -1 for none. */
    @FunctionalInterface
    interface ItemTint {
        int color(ItemStack stack, int tintIndex);
    }

    <M extends AbstractContainerMenu, S extends Screen & MenuAccess<M>> void registerMenuScreen(
            Supplier<? extends MenuType<? extends M>> type, ScreenFactory<M, S> factory);

    /** Vanilla's MenuScreens.ScreenConstructor, which isn't public in vanilla. */
    @FunctionalInterface
    interface ScreenFactory<M extends AbstractContainerMenu, S extends Screen & MenuAccess<M>> {
        S create(M menu, Inventory inventory, Component title);
    }
}
