package com.nanobuntv.cartridge.rom;

import com.nanobuntv.cartridge.Cartridge;
import com.nanobuntv.cartridge.blockentity.CartridgeWriterBlockEntity;
import com.nanobuntv.cartridge.config.CartridgeConfig;
import com.nanobuntv.cartridge.config.ServerSettings;
import com.nanobuntv.cartridge.emu.RomCache;
import com.nanobuntv.cartridge.item.CartridgeComponents;
import com.nanobuntv.cartridge.emu.ConsoleKind;
import com.nanobuntv.cartridge.item.CartridgeContents;
import com.nanobuntv.cartridge.item.GameMedia;
import com.nanobuntv.cartridge.menu.WriterMenu;
import com.nanobuntv.cartridge.net.RomAckPayload;
import com.nanobuntv.cartridge.net.RomDataPayload;
import com.nanobuntv.cartridge.net.RomListPayload;
import com.nanobuntv.cartridge.net.RomRequestPayload;
import com.nanobuntv.cartridge.net.RomUploadChunkPayload;
import com.nanobuntv.cartridge.net.RomUploadStartPayload;
import com.nanobuntv.cartridge.net.RomUploadStatusPayload;
import com.nanobuntv.cartridge.platform.Services;
import com.nanobuntv.cartridge.util.Compat;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.StringUtil;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.LevelResource;
import org.jetbrains.annotations.Nullable;

/**
 * The server half of moving ROMs around (D14, D16): uploads from the Cartridge Writer into the {@link RomStore}, and
 * downloads to clients whose consoles need a ROM or BIOS they haven't cached. Both directions are chunked and
 * acknowledged piece by piece, with a fixed window in flight, so a big file never floods a connection. One instance
 * per running server; payload handlers run on the server thread.
 */
public final class RomServer {
    /** Upload buffers live in memory (64 MB each at most by default), so only a few at once. */
    private static final int MAX_UPLOADS = 4;
    private static final long STALE_NANOS = 60_000_000_000L;
    private static final int DOWNLOAD_WINDOW = 1024 * 1024;

    @Nullable
    private static RomServer current;

    private final MinecraftServer server;
    private final RomStore store;
    private final Map<UUID, Upload> uploads = new HashMap<>();
    private final Map<UUID, Map<String, Download>> downloads = new HashMap<>();

    private RomServer(MinecraftServer server) throws IOException {
        this.server = server;
        this.store = new RomStore(server.getWorldPath(LevelResource.ROOT).resolve(Cartridge.MOD_ID));
    }

    /** The server stopped: drop its instance and any upload buffers. */
    public static void reset() {
        current = null;
    }

    /**
     * D126: once the server is up, checks every stored file against its hash off the server thread, moves damaged
     * ones aside, then tells everyone online which games are here (D125).
     */
    public static void onServerStarted(MinecraftServer server) {
        RomServer rs;
        try {
            rs = of(server);
        } catch (IOException e) {
            Cartridge.LOGGER.error("ROM store unavailable", e);
            return;
        }
        CompletableFuture.supplyAsync(() -> {
            try {
                return rs.store.verify();
            } catch (IOException e) {
                Cartridge.LOGGER.error("Couldn't check the ROM store", e);
                return List.<String>of();
            }
        }).thenAccept(damaged -> server.execute(() -> {
            if (current != rs) return; // the server stopped meanwhile
            for (String name : damaged) {
                Cartridge.LOGGER.warn("Stored file {} doesn't match its hash: moved to damaged/, so it counts as missing", name);
            }
            try {
                rs.store.rescan();
                if (!damaged.isEmpty()) {
                    RomListPayload list = new RomListPayload(true, rs.store.romHashes(), List.of());
                    server.getPlayerList().getPlayers().forEach(p -> Services.NETWORK.sendToPlayer(p, list));
                }
            } catch (IOException e) {
                Cartridge.LOGGER.error("ROM store unavailable", e);
            }
        }));
    }

    /** D125: a joining player learns which games are here. */
    public static void onPlayerJoin(ServerPlayer player) {
        try {
            Services.NETWORK.sendToPlayer(player, new RomListPayload(true, of(Compat.server(player)).store.romHashes(), List.of()));
        } catch (IOException e) {
            Cartridge.LOGGER.error("ROM store unavailable", e);
        }
    }

    /** D124: if this game's file is gone (removed by hand), tells everyone, so the Writer offers a restore. */
    public static void checkStored(MinecraftServer server, String sha1) {
        try {
            RomServer rs = of(server);
            if (rs.store.romFile(sha1).isPresent() && !rs.store.hasRom(sha1)) {
                rs.store.rescan();
                rs.broadcast(List.of(), List.of(sha1));
            }
        } catch (IOException e) {
            Cartridge.LOGGER.error("ROM store unavailable", e);
        }
    }

    /** D125: tells everyone a game was added or found missing. */
    private void broadcast(List<String> added, List<String> removed) {
        RomListPayload change = new RomListPayload(false, added, removed);
        server.getPlayerList().getPlayers().forEach(p -> Services.NETWORK.sendToPlayer(p, change));
    }

    /** The instance for this server, created on first use (a new world in the same game gets a new one). */
    private static RomServer of(MinecraftServer server) throws IOException {
        if (current == null || current.server != server) current = new RomServer(server);
        return current;
    }

    private static final class Upload {
        final int kind;
        final String system, fileName, title, sha1;
        final byte[] data;
        @Nullable final CartridgeWriterBlockEntity writer;
        final WriterMenu menu;
        int received;
        long lastActivity = System.nanoTime();

        Upload(RomUploadStartPayload start, String system, WriterMenu menu) {
            this.kind = start.kind();
            this.system = system;
            this.fileName = start.fileName();
            this.title = start.title();
            this.sha1 = start.sha1();
            this.data = new byte[start.size()];
            this.menu = menu;
            this.writer = start.kind() == RomUploadStartPayload.KIND_ROM ? menu.writer() : null;
        }
    }

    private static final class Download {
        final String sha1;
        final int kind;
        final Path file;
        final int size;
        int sent, acked;
        long lastActivity = System.nanoTime();

        Download(String sha1, int kind, Path file, int size) {
            this.sha1 = sha1;
            this.kind = kind;
            this.file = file;
            this.size = size;
        }
    }

    // --- Uploads ---------------------------------------------------------------------------------------------------

    public static void onUploadStart(RomUploadStartPayload start, ServerPlayer player) {
        try {
            of(Compat.server(player)).uploadStart(start, player);
        } catch (IOException e) {
            Cartridge.LOGGER.error("ROM store unavailable", e);
            reject(player, Component.translatable("cartridge_mod.writer.error.store", e.getMessage()));
        }
    }

    public static void onUploadChunk(RomUploadChunkPayload chunk, ServerPlayer player) {
        if (current != null && current.server == Compat.server(player)) current.uploadChunk(chunk, player);
    }

    /** The player closed the Writer, asked to stop, or left. */
    public static void cancelUpload(ServerPlayer player) {
        if (current != null && current.server == Compat.server(player)) current.dropUpload(player.getUUID());
    }

    private void uploadStart(RomUploadStartPayload start, ServerPlayer player) throws IOException {
        dropUpload(player.getUUID());
        sweep();
        ServerSettings config = CartridgeConfig.server();
        if (!(player.containerMenu instanceof WriterMenu menu) || !menu.stillValid(player)) {
            reject(player, Component.translatable("cartridge_mod.writer.error.closed"));
            return;
        }
        if (!Compat.hasPermissionLevel(player, config.uploadPermissionLevel)) {
            reject(player, Component.translatable("cartridge_mod.writer.error.permission"));
            return;
        }
        if (start.size() <= 0 || start.size() > config.maxRomSizeBytes()) {
            reject(player, Component.translatable("cartridge_mod.writer.error.too_big", megabytes(config.maxRomSizeBytes())));
            return;
        }

        if (start.kind() == RomUploadStartPayload.KIND_BIOS) {
            Optional<RomFormats.Bios> bios = RomFormats.biosBySha1(start.sha1());
            if (bios.isEmpty() || bios.get().size() != start.size()) {
                reject(player, Component.translatable("cartridge_mod.writer.error.wrong_bios"));
                return;
            }
            if (store.hasBios(bios.get())) {
                status(player, RomUploadStatusPayload.BIOS_DONE, Component.translatable("cartridge_mod.writer.bios_done"));
                return;
            }
        } else if (start.kind() == RomUploadStartPayload.KIND_RESTORE) {
            CartridgeContents cart = restoring(menu);
            if (cart == null || !cart.romSha1().equals(start.sha1())) {
                reject(player, Component.translatable("cartridge_mod.writer.error.not_same_game"));
                return;
            }
            if (store.hasRom(start.sha1())) {
                status(player, RomUploadStatusPayload.DONE, Component.translatable("cartridge_mod.writer.restored", cart.title()));
                broadcast(List.of(start.sha1()), List.of());
                return;
            }
            long quota = config.romStorageQuotaMegabytes * 1024L * 1024L;
            if (store.usedBytes() + start.size() > quota) {
                reject(player, Component.translatable("cartridge_mod.writer.error.full"));
                return;
            }
        } else {
            CartridgeWriterBlockEntity writer = menu.writer();
            if (writer == null || !writer.hasBlank()) {
                reject(player, Component.translatable("cartridge_mod.writer.error.no_blank"));
                return;
            }
            // The client's guess until the file is here; for a ROM the server already has, the stored header.
            String system = store.hasRom(start.sha1())
                    ? RomFormats.detect(store.romHeader(start.sha1())).orElse(knownSystem(start))
                    : knownSystem(start);
            if (needsBios(system)) {
                status(player, RomUploadStatusPayload.NEED_BIOS, Component.translatable("cartridge_mod.writer.need_bios"));
                return;
            }
            if (store.hasRom(start.sha1())) {
                // D16: stored once. Anyone with the same file burns it without sending it again.
                burn(player, writer, system, start.sha1(), start.title(), start.fileName());
                return;
            }
            long quota = config.romStorageQuotaMegabytes * 1024L * 1024L;
            if (store.usedBytes() + start.size() > quota) {
                reject(player, Component.translatable("cartridge_mod.writer.error.full"));
                return;
            }
        }
        if (uploads.size() >= MAX_UPLOADS) {
            reject(player, Component.translatable("cartridge_mod.writer.error.busy"));
            return;
        }
        Upload upload = new Upload(start, knownSystem(start), menu);
        uploads.put(player.getUUID(), upload);
        if (upload.writer != null) upload.writer.setBusy(true);
        Services.NETWORK.sendToPlayer(player, RomUploadStatusPayload.send(0));
    }

    private void uploadChunk(RomUploadChunkPayload chunk, ServerPlayer player) {
        Upload upload = uploads.get(player.getUUID());
        if (upload == null) return; // cancelled; the client has been told
        if (chunk.offset() != upload.received || chunk.data().length == 0
                || chunk.data().length > upload.data.length - upload.received) {
            dropUpload(player.getUUID());
            reject(player, Component.translatable("cartridge_mod.writer.error.transfer"));
            return;
        }
        System.arraycopy(chunk.data(), 0, upload.data, upload.received, chunk.data().length);
        upload.received += chunk.data().length;
        upload.lastActivity = System.nanoTime();
        if (upload.received < upload.data.length) {
            Services.NETWORK.sendToPlayer(player, RomUploadStatusPayload.send(upload.received));
            return;
        }
        dropUpload(player.getUUID());
        try {
            finish(upload, player);
        } catch (IOException e) {
            Cartridge.LOGGER.error("Couldn't store an upload from {}", player.getGameProfile().getName(), e);
            reject(player, Component.translatable("cartridge_mod.writer.error.store", e.getMessage()));
        }
    }

    private void finish(Upload upload, ServerPlayer player) throws IOException {
        if (!RomCache.sha1(upload.data).equals(upload.sha1)) {
            reject(player, Component.translatable("cartridge_mod.writer.error.transfer"));
            return;
        }
        if (upload.kind == RomUploadStartPayload.KIND_BIOS) {
            RomFormats.Bios bios = RomFormats.biosBySha1(upload.sha1).orElseThrow();
            store.putBios(bios, upload.data);
            Cartridge.LOGGER.info("{} installed the {} BIOS", player.getGameProfile().getName(), bios.system());
            status(player, RomUploadStatusPayload.BIOS_DONE, Component.translatable("cartridge_mod.writer.bios_done"));
            return;
        }

        if (upload.kind == RomUploadStartPayload.KIND_RESTORE) {
            CartridgeContents cart = player.containerMenu == upload.menu ? restoring(upload.menu) : null;
            if (cart == null || !cart.romSha1().equals(upload.sha1)) {
                reject(player, Component.translatable("cartridge_mod.writer.error.not_same_game"));
                return;
            }
            store.putRom(upload.sha1, upload.data);
            broadcast(List.of(upload.sha1), List.of());
            Cartridge.LOGGER.info("{} restored the game file of \"{}\" ({})", player.getGameProfile().getName(),
                    cart.title(), upload.sha1);
            status(player, RomUploadStatusPayload.DONE, Component.translatable("cartridge_mod.writer.restored", cart.title()));
            return;
        }

        Optional<String> detected = RomFormats.detect(upload.data);
        // A game without a header (an arcade romset, D197) is its console's to check.
        var check = ConsoleKind.forSystem(upload.system).map(ConsoleKind::gameCheck).orElse(null);
        if (detected.isEmpty() && check != null) {
            if (!check.test(upload.data)) {
                reject(player, Component.translatable("cartridge_mod.writer.error.format"));
                return;
            }
        } else if (detected.isEmpty() && CartridgeConfig.server().validateRomHeaders) {
            reject(player, Component.translatable("cartridge_mod.writer.error.format"));
            return;
        }
        String system = detected.orElse(upload.system);
        if (needsBios(system)) {
            status(player, RomUploadStatusPayload.NEED_BIOS, Component.translatable("cartridge_mod.writer.need_bios"));
            return;
        }
        // The player may have closed the Writer or taken the blank out while the file was on its way.
        CartridgeWriterBlockEntity writer = upload.writer;
        if (player.containerMenu != upload.menu || writer == null || writer.isRemoved() || !writer.hasBlank()) {
            reject(player, Component.translatable("cartridge_mod.writer.error.no_blank"));
            return;
        }
        store.putRom(upload.sha1, upload.data);
        broadcast(List.of(upload.sha1), List.of());
        burn(player, writer, system, upload.sha1, upload.title, upload.fileName);
    }

    private void burn(ServerPlayer player, CartridgeWriterBlockEntity writer, String system, String sha1, String title,
            String fileName) {
        String name = cleanTitle(title, fileName);
        GameMedia media = writer.blankMedia().orElse(null);
        if (media != null && !media.takes(system)) { // a cartridge's game onto a board, or a board's onto a cart
            reject(player, Component.translatable("cartridge_mod.writer.error.wrong_media", media.displayName()));
            return;
        }
        if (!writer.burn(new CartridgeContents(system, sha1, name, UUID.randomUUID()))) {
            reject(player, Component.translatable("cartridge_mod.writer.error.no_blank"));
            return;
        }
        Cartridge.LOGGER.info("{} burned \"{}\" ({}, {})", player.getGameProfile().getName(), name, system, sha1);
        player.level().playSound(null, writer.getBlockPos(), SoundEvents.NOTE_BLOCK_BIT.value(), SoundSource.BLOCKS, 0.6F, 1.6F);
        status(player, RomUploadStatusPayload.DONE, Component.translatable("cartridge_mod.writer.burned", name));
    }

    /** The burned cart in the open Writer's main slot, for a restore (D124), or null. */
    @Nullable
    private static CartridgeContents restoring(WriterMenu menu) {
        CartridgeWriterBlockEntity writer = menu.writer();
        if (writer == null || writer.isRemoved()) return null;
        ItemStack main = writer.getItem(CartridgeWriterBlockEntity.MAIN);
        return CartridgeWriterBlockEntity.isBurned(main) ? CartridgeComponents.CARTRIDGE.get(main) : null;
    }

    /** D44: the title the player typed, or one made from the file name (D116). */
    static String cleanTitle(String title, String fileName) {
        String name = StringUtil.filterText(title).strip();
        if (name.isEmpty()) name = RomFormats.titleFromFileName(StringUtil.filterText(fileName).strip());
        if (name.length() > RomUploadStartPayload.MAX_TITLE) name = name.substring(0, RomUploadStartPayload.MAX_TITLE).strip();
        return name.isEmpty() ? "Untitled" : name;
    }

    /** The client's system if it's one we know, else a guess from the file name. */
    private static String knownSystem(RomUploadStartPayload start) {
        String system = start.system();
        return GameMedia.values().stream().anyMatch(m -> m.takes(system)) ? system : RomFormats.fromFileName(start.fileName());
    }

    private boolean needsBios(String system) {
        return RomFormats.bios(system).filter(b -> !store.hasBios(b)).isPresent();
    }

    private void dropUpload(UUID player) {
        Upload upload = uploads.remove(player);
        if (upload != null && upload.writer != null && uploads.values().stream().noneMatch(u -> u.writer == upload.writer)) {
            upload.writer.setBusy(false);
        }
    }

    /** Forgets transfers of players who left or went quiet. */
    private void sweep() {
        long now = System.nanoTime();
        for (UUID id : uploads.keySet().toArray(UUID[]::new)) {
            Upload upload = uploads.get(id);
            if (server.getPlayerList().getPlayer(id) == null || now - upload.lastActivity > STALE_NANOS) dropUpload(id);
        }
        for (Iterator<Map.Entry<UUID, Map<String, Download>>> it = downloads.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<UUID, Map<String, Download>> entry = it.next();
            entry.getValue().values().removeIf(d -> now - d.lastActivity > STALE_NANOS);
            if (entry.getValue().isEmpty() || server.getPlayerList().getPlayer(entry.getKey()) == null) it.remove();
        }
    }

    // --- Downloads -------------------------------------------------------------------------------------------------

    public static void onRequest(RomRequestPayload request, ServerPlayer player) {
        try {
            of(Compat.server(player)).request(request, player);
        } catch (IOException e) {
            Cartridge.LOGGER.error("ROM store unavailable", e);
            Services.NETWORK.sendToPlayer(player, new RomDataPayload(request.kind(), request.sha1(), -1, 0, new byte[0]));
        }
    }

    public static void onAck(RomAckPayload ack, ServerPlayer player) {
        if (current != null && current.server == Compat.server(player)) current.ack(ack, player);
    }

    private void request(RomRequestPayload request, ServerPlayer player) throws IOException {
        sweep();
        Path file = null;
        if (request.kind() == RomUploadStartPayload.KIND_ROM) {
            file = store.romFile(request.sha1()).orElse(null);
        } else {
            Optional<RomFormats.Bios> bios = RomFormats.biosBySha1(request.sha1());
            if (bios.isPresent() && store.hasBios(bios.get())) file = store.biosFile(bios.get());
        }
        if (file == null || !Files.isRegularFile(file)) {
            if (request.kind() == RomUploadStartPayload.KIND_ROM && file != null) {
                // Removed by hand while carts still hold it (D124-D126): everyone's tooltips and Writers learn of it.
                store.rescan();
                broadcast(List.of(), List.of(request.sha1()));
            }
            Services.NETWORK.sendToPlayer(player, new RomDataPayload(request.kind(), request.sha1(), -1, 0, new byte[0]));
            return;
        }
        Download download = new Download(request.sha1(), request.kind(), file, (int) Files.size(file));
        downloads.computeIfAbsent(player.getUUID(), id -> new HashMap<>()).put(request.sha1(), download);
        pump(download, player);
    }

    private void ack(RomAckPayload ack, ServerPlayer player) {
        Map<String, Download> mine = downloads.get(player.getUUID());
        Download download = mine == null ? null : mine.get(ack.sha1());
        if (download == null) return;
        download.acked = Math.max(download.acked, Math.min(ack.received(), download.sent));
        download.lastActivity = System.nanoTime();
        if (download.acked >= download.size) {
            mine.remove(ack.sha1());
            return;
        }
        try {
            pump(download, player);
        } catch (IOException e) {
            Cartridge.LOGGER.warn("Couldn't read {} for a download", download.file, e);
            mine.remove(ack.sha1());
            Services.NETWORK.sendToPlayer(player, new RomDataPayload(download.kind, download.sha1, -1, 0, new byte[0]));
        }
    }

    /** Sends pieces until the window is full. An empty file is sent as one empty piece. */
    private static void pump(Download d, ServerPlayer player) throws IOException {
        if (d.size == 0) {
            Services.NETWORK.sendToPlayer(player, new RomDataPayload(d.kind, d.sha1, 0, 0, new byte[0]));
            return;
        }
        if (d.sent >= d.size || d.sent - d.acked >= DOWNLOAD_WINDOW) return;
        try (FileChannel channel = FileChannel.open(d.file, StandardOpenOption.READ)) {
            while (d.sent < d.size && d.sent - d.acked < DOWNLOAD_WINDOW) {
                ByteBuffer piece = ByteBuffer.allocate(Math.min(RomDataPayload.MAX_CHUNK, d.size - d.sent));
                while (piece.hasRemaining()) {
                    if (channel.read(piece, d.sent + piece.position()) < 0) throw new IOException("file shrank");
                }
                Services.NETWORK.sendToPlayer(player, new RomDataPayload(d.kind, d.sha1, d.size, d.sent, piece.array()));
                d.sent += piece.capacity();
            }
        }
    }

    // --- Replies ---------------------------------------------------------------------------------------------------

    private static void status(ServerPlayer player, int state, Component message) {
        Services.NETWORK.sendToPlayer(player, new RomUploadStatusPayload(state, 0, message));
    }

    private static void reject(ServerPlayer player, Component message) {
        status(player, RomUploadStatusPayload.REJECTED, message);
    }

    private static String megabytes(int bytes) {
        return String.valueOf(Math.max(1, bytes / (1024 * 1024)));
    }
}
