package com.nanobuntv.cartridge.client;

import com.nanobuntv.cartridge.item.HandheldItem;
import com.nanobuntv.cartridge.item.CartridgeItems;
import com.nanobuntv.cartridge.blockentity.CartridgeBlockEntities;
import com.nanobuntv.cartridge.blockentity.CartridgeWriterBlockEntity;
import com.nanobuntv.cartridge.blockentity.ConsoleBlockEntity;
import com.nanobuntv.cartridge.blockentity.TvBlockEntity;
import com.nanobuntv.cartridge.config.CartridgeConfig;
import com.nanobuntv.cartridge.emu.SharedMemory;
import com.nanobuntv.cartridge.item.CartridgeItem;
import com.nanobuntv.cartridge.item.ItemHelp;
import com.nanobuntv.cartridge.menu.CartridgeMenus;
import com.nanobuntv.cartridge.net.CartridgeNetwork;
import com.nanobuntv.cartridge.platform.ClientEvents;
import com.nanobuntv.cartridge.platform.ClientServices;
import com.nanobuntv.cartridge.platform.Services;
import net.minecraft.client.Minecraft;
//? if >=1.21.4 {
/*import com.nanobuntv.cartridge.Cartridge;
import com.nanobuntv.cartridge.mixin.client.SelectItemModelPropertiesAccessor;
import com.nanobuntv.cartridge.mixin.client.SpecialModelRenderersAccessor;
*///?}

/** Common client entry point, called by each loader's client initializer after {@code Cartridge.init()}. */
public final class CartridgeClient {
    private CartridgeClient() {}

    public static void init() {
        CartridgeConfig.loadClient(); // D122
        ClientEvents events = ClientServices.EVENTS;
        events.<TvBlockEntity>registerBlockEntityRenderer(CartridgeBlockEntities.CRT_TV, TvRenderer::new);
        events.<ConsoleBlockEntity>registerBlockEntityRenderer(CartridgeBlockEntities.CONSOLE_8BIT, ConsoleRenderer::new);
        events.<CartridgeWriterBlockEntity>registerBlockEntityRenderer(CartridgeBlockEntities.CARTRIDGE_WRITER, WriterRenderer::new);
        //? if >=1.21.4 {
        /*// The cartridge's shape per console and the handhelds' screens, for their item model definitions.
        SelectItemModelPropertiesAccessor.cartridge$ids().put(Cartridge.id("cartridge_shape"), CartridgeShapeProperty.TYPE);
        SpecialModelRenderersAccessor.cartridge$ids().put(Cartridge.id("handheld_screen"), HandheldScreenRenderer.Unbaked.MAP_CODEC);
        *///?}
        // The dyed handhelds' shells (D169, D185).
        events.registerItemColor(HandheldItem::tint, CartridgeItems.POCKETRON_COLOR, CartridgeItems.POCKETRON_ADVANCE,
                CartridgeItems.POCKETRON_ADVANCE_SP);
        events.registerMenuScreen(CartridgeMenus.CONSOLE_8BIT, ConsoleScreen::new);
        events.registerMenuScreen(CartridgeMenus.CARTRIDGE_WRITER, WriterScreen::new);
        events.registerMenuScreen(CartridgeMenus.POCKETRON_ADAPTER, AdapterScreen::new);
        events.registerMenuScreen(CartridgeMenus.HANDHELD, ConsoleScreen::new);

        events.onTickEnd(() -> {
            ClientConsoles.endTick();
            PlayMode.tick();
            DevSelfTest.tick();
        });
        events.onHudRender(PlayMode::renderHud);
        events.onRenderLevel(LinkCords::render); // D180
        // Fabric reports leaving on the network thread; textures are closed on the render thread.
        events.onDisconnect(() -> Minecraft.getInstance().execute(() -> {
            PlayMode.leave(false);
            ClientConsoles.clear();
            RomDownloads.clear();
            CartridgeConfig.setSynced(null);
            RomAvailability.clear(); // back to defaults until the next server sends its own (D123)
        }));

        ConsoleBlockEntity.clientTicker = ClientConsoles::observe;
        ItemHelp.shiftDown = ClientCompat::shiftDown;
        CartridgeItem.romMissing = RomAvailability::missing;
        CartridgeNetwork.onRomList = RomAvailability::onList;
        CartridgeNetwork.onEnterPlayMode = payload -> PlayMode.enter(payload.game(), payload.port());
        CartridgeNetwork.onUploadStatus = payload -> {
            if (Minecraft.getInstance().screen instanceof WriterScreen writer) writer.onStatus(payload);
        };
        CartridgeNetwork.onRomData = RomDownloads::onData;
        CartridgeNetwork.onSaveFile = payload -> {
            if (Minecraft.getInstance().screen instanceof WriterScreen writer) writer.onSaveFile(payload);
        };
        CartridgeNetwork.onSession = ClientConsoles::deliver;

        // Shared-memory files left by a crashed session.
        SharedMemory.cleanStale(SharedMemory.directory(Services.PLATFORM.gameDir()), "console-");
    }
}
