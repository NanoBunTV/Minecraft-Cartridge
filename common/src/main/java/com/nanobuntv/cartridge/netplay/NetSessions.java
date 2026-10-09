package com.nanobuntv.cartridge.netplay;

import com.nanobuntv.cartridge.Cartridge;
import com.nanobuntv.cartridge.blockentity.ConsoleBlockEntity;
import com.nanobuntv.cartridge.emu.ConsoleKind;
import com.nanobuntv.cartridge.item.CartridgeComponents;
import com.nanobuntv.cartridge.item.CartridgeContents;
import com.nanobuntv.cartridge.item.Handhelds;
import com.nanobuntv.cartridge.item.MemoryCardContents;
import com.nanobuntv.cartridge.menu.ConsoleMenu;
import com.nanobuntv.cartridge.net.ChecksumPayload;
import com.nanobuntv.cartridge.net.InputFramesPayload;
import com.nanobuntv.cartridge.net.SessionJoinPayload;
import com.nanobuntv.cartridge.net.SessionLeavePayload;
import com.nanobuntv.cartridge.net.SessionPingPayload;
import com.nanobuntv.cartridge.net.SramUploadPayload;
import com.nanobuntv.cartridge.net.StateUploadPayload;
import com.nanobuntv.cartridge.save.SaveServer;
import com.nanobuntv.cartridge.save.SaveStore;
import com.nanobuntv.cartridge.util.Compat;
import java.io.IOException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.LevelResource;
import org.jetbrains.annotations.Nullable;

/**
 * Every console's and handheld's {@link NetSession} on this server (D158), and the payload handlers that reach them
 * (server thread). A console's game is keyed by its dimension and position, a handheld's by its id. A session starts
 * when the first client joins a powered game and ends with the power, the cartridge, the block or the chunk; for a
 * handheld, once it has been put away and its clients have left (D164). The console block entity and item/Handhelds
 * report those changes here.
 */
public final class NetSessions {
    /** A game's place: a console in a dimension, or a handheld anywhere (dimension null). */
    private record Id(@Nullable ResourceKey<Level> dimension, GameKey key) {}

    private static final Map<Id, NetSession> SESSIONS = new HashMap<>();
    /** The newest core seen at each console or handheld, kept across restarts of its game (D89). */
    private static final Map<Id, String> LATEST_CORE = new HashMap<>();
    /**
     * Games that just ended, for a few seconds: the authority's last battery save still counts (power off, a pulled
     * cart, D95), though it arrives after the game is gone.
     */
    private static final Map<Id, Ended> ENDED = new HashMap<>();
    private static final long ENDED_GRACE = 10_000_000_000L;
    /** How long a put-away handheld's session waits for its clients to send where it got to (D164). */
    private static final long PUT_AWAY_GRACE = 10_000_000_000L;
    /** When each handheld's session found its handheld gone. */
    private static final Map<Id, Long> PUT_AWAY = new HashMap<>();
    /** Battery saves on their way, per player. */
    private static final Map<UUID, SramUpload> SRAM_UPLOADS = new HashMap<>();

    private record Ended(int bootId, List<UUID> carts, @Nullable UUID source, long until) {}

    private record SramUpload(GameKey game, int bootId, byte[] data, int[] received) {}
    @Nullable
    private static MinecraftServer server;

    private NetSessions() {}

    /** A new server (another world in the same game) starts from nothing. */
    private static void bind(@Nullable MinecraftServer s) {
        if (s == null || s == server) return;
        reset();
        server = s;
    }

    /** The server stopped: forget it and everything of its worlds. */
    public static void reset() {
        SESSIONS.clear();
        LATEST_CORE.clear();
        ENDED.clear();
        PUT_AWAY.clear();
        SRAM_UPLOADS.clear();
        server = null;
    }

    private static Id id(Level level, GameKey key) {
        return new Id(key.isHandheld() ? null : level.dimension(), key);
    }

    @Nullable
    private static NetSession session(Level level, GameKey key) {
        bind(level.getServer());
        return SESSIONS.get(id(level, key));
    }

    @Nullable
    private static NetSession session(ServerPlayer player, GameKey key, int bootId) {
        NetSession s = session(Compat.level(player), key);
        return s != null && s.bootId == bootId ? s : null;
    }

    /** The console (in this level) or handheld (anywhere) a key names, if it's there now. */
    @Nullable
    private static GameHost host(ServerLevel level, GameKey key) {
        if (key.isHandheld()) return Handhelds.host(level.getServer(), key.handheld());
        BlockPos pos = key.console();
        return level.isLoaded(pos) && level.getBlockEntity(pos) instanceof ConsoleBlockEntity console ? console : null;
    }

    /** A session's console or handheld, from wherever it is now. */
    @Nullable
    private static GameHost host(MinecraftServer server, Id id) {
        if (id.key().isHandheld()) return Handhelds.host(server, id.key().handheld());
        ServerLevel level = server.getLevel(id.dimension());
        return level != null ? host(level, id.key()) : null;
    }

    static Path resumeFile(ServerLevel level, GameKey key) {
        Path dir = level.getServer().getWorldPath(LevelResource.ROOT).resolve(Cartridge.MOD_ID);
        if (key.isHandheld()) return ResumeStore.handheldFile(dir, key.handheld());
        var dim = level.dimension().location();
        BlockPos pos = key.console();
        return ResumeStore.file(dir, dim.getNamespace(), dim.getPath(), pos.getX(), pos.getY(), pos.getZ());
    }

    // --- Payloads ------------------------------------------------------------------------------------------------------

    public static void onJoin(SessionJoinPayload p, ServerPlayer player) {
        ServerLevel level = Compat.level(player);
        bind(level.getServer());
        GameKey key = p.game();
        GameHost console = host(level, key);
        if (console == null || console.serverLevel() != level) return;
        CartridgeContents cart = console.contents();
        double reach = console.reach();
        if (!console.powered() || console.bootId() != p.bootId() || cart == null
                || player.distanceToSqr(console.where()) > reach * reach || !(p.fps() > 1 && p.fps() < 1000)) {
            return;
        }
        Id id = id(level, key);
        NetSession s = SESSIONS.get(id);
        if (s == null) {
            Path file = resumeFile(level, key);
            var resume = ResumeStore.read(file, p.bootId(), cart.cartId()).orElse(null);
            List<UUID> carts = console.saveCarts();
            byte[] sram = resume == null ? bootSram(level.getServer(), carts) : null;
            MinecraftServer mc = level.getServer();
            int ports = ConsoleKind.forSystem(cart.system()).map(ConsoleKind::ports).orElse(2);
            s = new NetSession(mc, key, () -> host(mc, id), p.bootId(), cart.cartId(), carts, file, p.fps(), ports, resume,
                    LATEST_CORE.get(id), sram);
            SESSIONS.put(id, s);
            PUT_AWAY.remove(id);
        }
        if (!coreMatches(s, id, player, p.core(), console)) return;
        s.join(player, p.core(), System.nanoTime());
    }

    /** The battery save a game starts with (D24): its cart's, or a linked pair's packed (D177); null for none. */
    @Nullable
    private static byte[] bootSram(MinecraftServer server, List<UUID> carts) {
        SaveStore store = SaveServer.store(server);
        if (carts.size() == 1) return store.readSram(carts.get(0)).orElse(null);
        List<byte[]> saves = new java.util.ArrayList<>();
        for (UUID cart : carts) saves.add(store.readSram(cart).orElse(new byte[0]));
        return LinkedSaves.pack(saves);
    }

    /**
     * D58, D89, D114: the core comes with the mod, so a different one means a different Cartridge version. While
     * nobody is in the game, a stored savestate from another core (a resume snapshot from before a mod update) is
     * dropped and the game restarts from power-on on the joiner's core; savestates don't carry across core builds.
     * Once players are in, a joiner with another core can't join.
     */
    private static boolean coreMatches(NetSession s, Id id, ServerPlayer player, String core, GameHost console) {
        if (s.refCore == null || s.refCore.equals(core)) return true;
        if (s.members.isEmpty()) {
            Cartridge.LOGGER.info("The saved game at {} is from another emulator core version: restarting it",
                    id.key().describe());
            LATEST_CORE.put(id, core);
            console.reboot(); // ends this session (changed)
        } else {
            s.sendCoreMismatch(player);
        }
        return false;
    }

    public static void onLeave(SessionLeavePayload p, ServerPlayer player) {
        NetSession s = session(player, p.game(), p.bootId());
        if (s != null) s.leave(player.getUUID(), System.nanoTime());
    }

    public static void onInputs(InputFramesPayload p, ServerPlayer player) {
        NetSession s = session(player, p.game(), p.bootId());
        if (s != null) s.onInputs(player, p.port(), p.start(), p.buttons(), System.nanoTime());
    }

    public static void onChecksum(ChecksumPayload p, ServerPlayer player) {
        NetSession s = session(player, p.game(), p.bootId());
        if (s != null) s.onChecksum(player, p.frame(), p.checksum(), System.nanoTime());
    }

    public static void onStateUpload(StateUploadPayload p, ServerPlayer player) {
        NetSession s = session(player, p.game(), p.bootId());
        if (s != null) s.onStateUpload(player, p, System.nanoTime());
    }

    public static void onPing(SessionPingPayload p, ServerPlayer player) {
        NetSession s = session(player, p.game(), p.bootId());
        if (s != null) s.onPing(player, p.nonce(), p.rttMs(), System.nanoTime());
    }

    /** The authority's battery RAM (D95): it becomes the cartridge's battery save. */
    public static void onSramUpload(SramUploadPayload p, ServerPlayer player) {
        bind(Compat.server(player));
        UUID id = player.getUUID();
        SramUpload upload = SRAM_UPLOADS.get(id);
        if (p.offset() == 0) {
            SRAM_UPLOADS.remove(id);
            if (p.total() <= 0 || p.total() > SaveStore.MAX_SRAM) return;
            upload = new SramUpload(p.game(), p.bootId(), new byte[p.total()], new int[1]);
            SRAM_UPLOADS.put(id, upload);
        }
        if (upload == null) return;
        int received = upload.received()[0];
        if (!upload.game().equals(p.game()) || upload.bootId() != p.bootId() || p.offset() != received
                || p.total() != upload.data().length || p.data().length > upload.data().length - received) {
            SRAM_UPLOADS.remove(id);
            return;
        }
        System.arraycopy(p.data(), 0, upload.data(), received, p.data().length);
        upload.received()[0] = received + p.data().length;
        if (upload.received()[0] < upload.data().length) return;
        SRAM_UPLOADS.remove(id);

        List<UUID> carts = sramCarts(player, p.game(), p.bootId());
        if (carts == null) return;
        List<byte[]> saves = carts.size() == 1 ? List.of(upload.data()) : LinkedSaves.unpack(upload.data(), carts.size());
        if (saves == null) return;
        for (int i = 0; i < carts.size(); i++) {
            if (carts.size() > 1 && saves.get(i).length == 0) continue; // that Game Boy's game has no battery
            try {
                SaveServer.store(Compat.server(player)).writeSram(carts.get(i), saves.get(i));
            } catch (IOException e) {
                Cartridge.LOGGER.error("Couldn't store the battery save of cartridge {}", carts.get(i), e);
            }
        }
    }

    /** The cartridges whose battery saves this player may write now: theirs is the authority's client (D85, D95). */
    @Nullable
    private static List<UUID> sramCarts(ServerPlayer player, GameKey key, int bootId) {
        NetSession s = session(player, key, bootId);
        if (s != null) return player.getUUID().equals(s.sourceId()) ? s.saveCarts : null;
        long now = System.nanoTime();
        ENDED.values().removeIf(e -> now > e.until());
        Ended ended = ENDED.get(id(Compat.level(player), key));
        return ended != null && ended.bootId() == bootId && player.getUUID().equals(ended.source()) ? ended.carts() : null;
    }

    // --- The console's side --------------------------------------------------------------------------------------------

    private static GameKey key(ConsoleBlockEntity console) {
        return GameKey.console(console.getBlockPos());
    }

    /** Every server tick, for each loaded console. */
    public static void tick(ConsoleBlockEntity console) {
        NetSession s = session(console.getLevel(), key(console));
        if (s != null) s.tick(System.nanoTime());
    }

    /**
     * The console or handheld changed (anything synced to clients). Power off, a new power-on, or the cartridge pulled
     * or swapped ends the game and forgets its resume snapshot; otherwise the pad holders may have changed.
     */
    public static void changed(GameHost console) {
        NetSession s = session(console.serverLevel(), console.gameKey());
        if (s == null) return;
        CartridgeContents cart = console.contents();
        if (!console.powered() || console.bootId() != s.bootId || cart == null || !cart.cartId().equals(s.cartId)) {
            end(s, true);
        } else {
            s.updateHeld(System.nanoTime());
            s.updateDevice(System.nanoTime());
        }
    }

    /** RESET (D28): stamped onto a frame of the running game. False if no game is running here: power it up afresh. */
    public static boolean reset(ConsoleBlockEntity console) {
        NetSession s = session(console.getLevel(), key(console));
        if (s == null || s.syncedCount() == 0) return false;
        s.schedule(SessionTimeline.EVENT_RESET, System.nanoTime());
        return true;
    }

    /** Whether a game is running here that clients are emulating (the GUI's Save and Load buttons, D92). */
    public static boolean running(GameHost console) {
        NetSession s = session(console.serverLevel(), console.gameKey());
        return s != null && s.syncedCount() > 0;
    }

    /** Save on the GUI's Memory Card (D92): anyone may. Returns a {@link ConsoleMenu} result. */
    public static int saveCard(GameHost console, ServerPlayer player) {
        NetSession s = session(console.serverLevel(), console.gameKey());
        if (console.memoryCard().isEmpty()) return ConsoleMenu.RESULT_NO_CARD;
        if (s == null || console.contents() == null) return ConsoleMenu.RESULT_NO_GAME;
        return s.saveCard(player, console.memoryCard(), System.nanoTime());
    }

    /**
     * Load the GUI's Memory Card (D92): only Player 1, or anyone while nobody holds pad 1 (D93), and only into the
     * same game, from any cart of it (D94). Returns a {@link ConsoleMenu} result.
     */
    public static int loadCard(GameHost console, ServerPlayer player) {
        UUID playerOne = console.holder(0);
        if (playerOne != null && !playerOne.equals(player.getUUID())) return ConsoleMenu.RESULT_PLAYER_ONE;
        MemoryCardContents card = CartridgeComponents.MEMORY_CARD.get(console.memoryCard());
        if (card == null) return console.memoryCard().isEmpty() ? ConsoleMenu.RESULT_NO_CARD : ConsoleMenu.RESULT_EMPTY;
        CartridgeContents cart = console.contents();
        NetSession s = session(console.serverLevel(), console.gameKey());
        if (cart == null || s == null) return ConsoleMenu.RESULT_NO_GAME;
        if (!card.romSha1().equals(cart.romSha1())) return ConsoleMenu.RESULT_OTHER_GAME;
        Optional<SaveStore.CardState> state = SaveServer.store(Compat.server(player)).readCard(card.cardId());
        if (state.isEmpty()) return ConsoleMenu.RESULT_MISSING;
        if (!state.get().romSha1().equals(cart.romSha1())) return ConsoleMenu.RESULT_OTHER_GAME;
        int result = s.loadCard(state.get(), System.nanoTime());
        if (result == ConsoleMenu.RESULT_LOADED) {
            Cartridge.LOGGER.info("{} loaded Memory Card {} into \"{}\" at {}", player.getGameProfile().getName(),
                    card.cardId(), cart.title(), console.gameKey().describe());
        }
        return result;
    }

    /** The console GUI's Swap disk button (D74). */
    public static void swapDisk(ConsoleBlockEntity console) {
        NetSession s = session(console.getLevel(), key(console));
        if (s != null && s.syncedCount() > 0) s.schedule(SessionTimeline.EVENT_DISK_SWAP, System.nanoTime());
    }

    /** The chunk unloaded: the game stops here, its resume snapshot stays (D90). */
    public static void unloaded(ConsoleBlockEntity console) {
        Level level = console.getLevel();
        if (level == null || level.isClientSide) return;
        NetSession s = session(level, key(console));
        if (s != null) end(s, false);
    }

    /** The console was broken: its game and resume snapshot go. */
    public static void broken(ServerLevel level, BlockPos pos) {
        NetSession s = session(level, GameKey.console(pos));
        if (s != null) end(s, true);
        ResumeStore.delete(resumeFile(level, GameKey.console(pos)));
    }

    /** A handheld or a link's game ends for good (linked, unlinked, D177): its session and resume snapshot go. */
    public static void endGame(MinecraftServer s, GameKey key) {
        bind(s);
        NetSession session = SESSIONS.get(new Id(null, key));
        if (session != null) end(session, true);
        ResumeStore.delete(resumeFile(s.overworld(), key));
    }

    /** POWER off or a fresh boot with no game running: forget any resume snapshot (D90, D164). */
    public static void forget(ServerLevel level, GameKey key) {
        ResumeStore.delete(resumeFile(level, key));
    }

    // --- Handhelds (D158, D164) ----------------------------------------------------------------------------------------

    /**
     * Every server tick: each handheld's game runs on; one that's been put away ends, keeping its resume snapshot,
     * once its clients have left (they send where it got to as they go) or after a grace period.
     */
    public static void tickHandhelds(MinecraftServer s) {
        bind(s);
        long now = System.nanoTime();
        for (Map.Entry<Id, NetSession> e : List.copyOf(SESSIONS.entrySet())) {
            if (!e.getKey().key().isHandheld()) continue;
            NetSession session = e.getValue();
            session.tick(now);
            if (Handhelds.host(s, e.getKey().key().handheld()) != null) {
                PUT_AWAY.remove(e.getKey());
                continue;
            }
            long since = PUT_AWAY.computeIfAbsent(e.getKey(), k -> now);
            if (session.members.isEmpty() || now - since > PUT_AWAY_GRACE) end(session, false);
        }
    }

    private static void end(NetSession s, boolean forget) {
        Id id = null;
        for (Map.Entry<Id, NetSession> e : SESSIONS.entrySet()) {
            if (e.getValue() == s) id = e.getKey();
        }
        if (id == null) return;
        SESSIONS.remove(id);
        PUT_AWAY.remove(id);
        ENDED.put(id, new Ended(s.bootId, s.saveCarts, s.sourceId(), System.nanoTime() + ENDED_GRACE));
        s.close(forget);
    }
}
