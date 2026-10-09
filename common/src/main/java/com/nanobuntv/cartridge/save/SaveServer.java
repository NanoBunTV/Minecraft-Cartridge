package com.nanobuntv.cartridge.save;

import com.nanobuntv.cartridge.Cartridge;
import com.nanobuntv.cartridge.blockentity.CartridgeWriterBlockEntity;
import com.nanobuntv.cartridge.config.CartridgeConfig;
import com.nanobuntv.cartridge.item.CartridgeComponents;
import com.nanobuntv.cartridge.item.CartridgeContents;
import com.nanobuntv.cartridge.menu.WriterMenu;
import com.nanobuntv.cartridge.net.SaveExportPayload;
import com.nanobuntv.cartridge.net.SaveFilePayload;
import com.nanobuntv.cartridge.net.SaveImportPayload;
import com.nanobuntv.cartridge.platform.Services;
import com.nanobuntv.cartridge.util.Compat;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.LevelResource;
import org.jetbrains.annotations.Nullable;

/**
 * The server's {@link SaveStore} and the Cartridge Writer's battery save buttons (D97): Export sends the save of the
 * cart in the player's open Writer, Import replaces it with a {@code .sav} file the player picked. Server thread.
 */
public final class SaveServer {
    @Nullable
    private static MinecraftServer server;
    @Nullable
    private static SaveStore store;
    /** Imports on their way, per player. */
    private static final Map<UUID, Import> IMPORTS = new HashMap<>();

    private record Import(WriterMenu menu, UUID cartId, byte[] data, int[] received) {}

    private SaveServer() {}

    /** The server stopped: forget it. */
    public static void reset() {
        server = null;
        store = null;
        IMPORTS.clear();
    }

    /** This server's saves (a new world in the same game gets its own). */
    public static SaveStore store(MinecraftServer s) {
        if (s != server || store == null) {
            server = s;
            store = new SaveStore(s.getWorldPath(LevelResource.ROOT).resolve(Cartridge.MOD_ID));
            IMPORTS.clear();
        }
        return store;
    }

    /** The burned cart in the player's open Writer, if any. */
    @Nullable
    private static CartridgeContents writerCart(ServerPlayer player) {
        if (!(player.containerMenu instanceof WriterMenu menu) || !menu.stillValid(player)) return null;
        CartridgeWriterBlockEntity writer = menu.writer();
        if (writer == null) return null;
        return CartridgeComponents.CARTRIDGE.get(writer.getItem(CartridgeWriterBlockEntity.MAIN));
    }

    public static void onExport(SaveExportPayload payload, ServerPlayer player) {
        CartridgeContents cart = writerCart(player);
        if (cart == null) {
            reply(player, SaveFilePayload.FAILED, Component.translatable("cartridge_mod.writer.save.no_cart"));
            return;
        }
        Optional<byte[]> save = store(Compat.server(player)).readSram(cart.cartId());
        if (save.isEmpty()) {
            reply(player, SaveFilePayload.FAILED, Component.translatable("cartridge_mod.writer.save.none", cart.title()));
            return;
        }
        Services.NETWORK.sendToPlayer(player, new SaveFilePayload(SaveFilePayload.EXPORT, Component.empty(),
                cart.title() + ".sav", save.get()));
    }

    public static void onImport(SaveImportPayload p, ServerPlayer player) {
        UUID id = player.getUUID();
        Import upload = IMPORTS.get(id);
        if (p.offset() == 0) {
            IMPORTS.remove(id);
            CartridgeContents cart = writerCart(player);
            if (cart == null) {
                reply(player, SaveFilePayload.FAILED, Component.translatable("cartridge_mod.writer.save.no_cart"));
                return;
            }
            if (!Compat.hasPermissionLevel(player, CartridgeConfig.server().uploadPermissionLevel)) {
                reply(player, SaveFilePayload.FAILED, Component.translatable("cartridge_mod.writer.save.permission"));
                return;
            }
            if (p.total() <= 0 || p.total() > SaveStore.MAX_SRAM) {
                reply(player, SaveFilePayload.FAILED, Component.translatable("cartridge_mod.writer.save.too_big",
                        SaveStore.MAX_SRAM / 1024));
                return;
            }
            upload = new Import((WriterMenu) player.containerMenu, cart.cartId(), new byte[p.total()], new int[1]);
            IMPORTS.put(id, upload);
        }
        if (upload == null) return; // refused at the start; the player has been told
        int received = upload.received()[0];
        if (p.offset() != received || p.total() != upload.data().length || p.data().length > upload.data().length - received) {
            IMPORTS.remove(id);
            reply(player, SaveFilePayload.FAILED, Component.translatable("cartridge_mod.writer.error.transfer"));
            return;
        }
        System.arraycopy(p.data(), 0, upload.data(), received, p.data().length);
        upload.received()[0] = received + p.data().length;
        if (upload.received()[0] < upload.data().length) return;
        IMPORTS.remove(id);

        // The cart must still be the one the import started with.
        CartridgeContents cart = writerCart(player);
        if (player.containerMenu != upload.menu() || cart == null || !cart.cartId().equals(upload.cartId())) {
            reply(player, SaveFilePayload.FAILED, Component.translatable("cartridge_mod.writer.save.no_cart"));
            return;
        }
        try {
            store(Compat.server(player)).writeSram(cart.cartId(), upload.data());
        } catch (IOException e) {
            Cartridge.LOGGER.error("Couldn't store an imported battery save", e);
            reply(player, SaveFilePayload.FAILED, Component.translatable("cartridge_mod.writer.error.store", e.getMessage()));
            return;
        }
        Cartridge.LOGGER.info("{} imported a battery save onto \"{}\" ({})", player.getGameProfile().getName(), cart.title(),
                cart.cartId());
        reply(player, SaveFilePayload.IMPORTED, Component.translatable("cartridge_mod.writer.save.imported", cart.title()));
    }

    /** The player closed the Writer or left. */
    public static void cancel(ServerPlayer player) {
        IMPORTS.remove(player.getUUID());
    }

    private static void reply(ServerPlayer player, int state, Component message) {
        Services.NETWORK.sendToPlayer(player, SaveFilePayload.message(state, message));
    }
}
