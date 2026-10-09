package com.nanobuntv.cartridge.netplay;

import com.nanobuntv.cartridge.Cartridge;
import com.nanobuntv.cartridge.config.CartridgeConfig;
import com.nanobuntv.cartridge.emu.ConsoleKind;
import com.nanobuntv.cartridge.item.CartridgeComponents;
import com.nanobuntv.cartridge.item.CartridgeContents;
import com.nanobuntv.cartridge.item.MemoryCardContents;
import com.nanobuntv.cartridge.menu.ConsoleMenu;
import com.nanobuntv.cartridge.net.CoreMismatchPayload;
import com.nanobuntv.cartridge.net.SessionInputsPayload;
import com.nanobuntv.cartridge.net.SessionLoadPayload;
import com.nanobuntv.cartridge.net.SessionPongPayload;
import com.nanobuntv.cartridge.net.SessionStatePayload;
import com.nanobuntv.cartridge.net.SessionSyncPayload;
import com.nanobuntv.cartridge.net.StateRequestPayload;
import com.nanobuntv.cartridge.net.StateUploadPayload;
import com.nanobuntv.cartridge.platform.Services;
import com.nanobuntv.cartridge.save.SaveServer;
import com.nanobuntv.cartridge.save.SaveStore;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.UUID;
import java.util.function.Supplier;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * One console's or handheld's networked game on the server (D12, D13, D83-D90, D158): who emulates it, who plays which port, the
 * {@link SessionTimeline}, and the newest savestate (the base everyone joins from). It never runs the game itself: it
 * relays inputs, collects savestates and checksums from the clients, and decides who has to catch up. Server thread
 * only; {@link NetSessions} owns the instances.
 */
final class NetSession {
    /** A joiner gets a fresh savestate if the stored one is more than this many frames old (about 5 s). */
    private static final int FRESH_FRAMES = 300;
    /** Past this many frames of inputs the server asks for a new savestate, so joiners don't replay too much. */
    private static final int MAX_LOG_FRAMES = 36_000;
    private static final int SYNC_SEGMENT_FRAMES = 32_768;
    private static final long REQUEST_TIMEOUT = 5_000_000_000L;
    private static final long RESYNC_INTERVAL = 5_000_000_000L;
    private static final long STATE_INTERVAL = 1_000_000_000L;
    private static final int MAX_STATE = 16 * 1024 * 1024;
    private static final int KEEP_CHECKSUMS = 32;

    static final class Member {
        final ServerPlayer player;
        final long joined = System.nanoTime();
        String core;
        boolean synced, waiting;
        int rttMs = 100;
        long lastResync;
        final TreeMap<Integer, Long> checks = new TreeMap<>();

        Member(ServerPlayer player) {
            this.player = player;
        }
    }

    private static final class Upload {
        final int requestId, frame;
        final byte[] data, thumbnail;
        int received;

        Upload(int requestId, int frame, int total, byte[] thumbnail) {
            this.requestId = requestId;
            this.frame = frame;
            this.data = new byte[total];
            this.thumbnail = thumbnail;
        }
    }

    /** A Memory Card waiting for the next savestate (D92); it must still be in the console when that arrives. */
    private record CardSave(ServerPlayer player, ItemStack card, long requested) {}

    final MinecraftServer server;
    final GameKey key;
    /** The console or handheld, looked up afresh each time (null while it's gone). */
    private final Supplier<GameHost> host;
    final int bootId;
    final UUID cartId;
    /** The cartridges whose battery saves this game writes: its own, or a linked pair's (D177). */
    final List<UUID> saveCarts;
    private final Path resumeFile;
    final SessionTimeline timeline;

    /** The savestate before {@code timeline.base()}; null means the game's power-on state. */
    @Nullable
    private byte[] baseState;
    /** The cartridge's battery save at power-on, for clients starting from there (D24); null for none. */
    @Nullable
    private final byte[] bootSram;
    @Nullable
    private String baseCore;
    /** The core version every member must have (D58, D114); null until the first one joins. */
    @Nullable
    String refCore;

    final Map<UUID, Member> members = new LinkedHashMap<>();
    private final UUID[] heldBy;
    private final List<Segment> spectatorSegments = new ArrayList<>();
    private final TreeMap<Integer, Integer> spectatorEvents = new TreeMap<>();
    private final TreeMap<Integer, Long> truth = new TreeMap<>();
    private final Map<UUID, Upload> uploads = new LinkedHashMap<>();
    /** Memory Card savestates loaded on a frame (EVENT_LOAD), kept while a joiner may still replay that frame. */
    private final TreeMap<Integer, byte[]> loads = new TreeMap<>();
    private final List<CardSave> cardSaves = new ArrayList<>();

    /** What the ports hold (a device word: the light gun D99, the mouse D144), as last scheduled. */
    private int devices;
    private int nextRequestId;
    private int requestId = -1;
    @Nullable
    private UUID requestFrom;
    private long requestSent, lastSnapshot, lastStateSent, lastTick;

    NetSession(MinecraftServer server, GameKey key, Supplier<GameHost> host, int bootId, UUID cartId, List<UUID> saveCarts,
            Path resumeFile, double fps, int ports, @Nullable ResumeStore.Resume resume, @Nullable String latestCore,
            @Nullable byte[] bootSram) {
        this.saveCarts = List.copyOf(saveCarts);
        this.server = server;
        this.key = key;
        this.host = host;
        this.bootId = bootId;
        this.cartId = cartId;
        this.resumeFile = resumeFile;
        this.timeline = new SessionTimeline(resume != null ? resume.frame() : 0, resume != null && resume.everHeld(), fps, ports);
        this.heldBy = new UUID[ports];
        this.bootSram = resume == null ? bootSram : null; // a resumed game's battery RAM is in its savestate
        if (resume != null) {
            baseState = resume.state();
            baseCore = resume.core();
            refCore = resume.core();
        } else {
            refCore = latestCore;
        }
        lastSnapshot = System.nanoTime();
        GameHost console = host.get();
        if (console != null && console.deviceWord() != 0) {
            devices = console.deviceWord();
            timeline.mark(timeline.base(), SessionTimeline.devicesEvent(devices));
        }
    }

    /**
     * A light gun (D99) or mouse (D144) was plugged in or pulled out: from a frame everyone reaches after hearing of
     * it.
     */
    void updateDevice(long now) {
        GameHost console = host.get();
        if (console == null || console.deviceWord() == devices) return;
        devices = console.deviceWord();
        schedule(SessionTimeline.devicesEvent(devices), now);
    }

    // --- Members --------------------------------------------------------------------------------------------------------

    /** A client loaded the game and wants in. The core check (D89) is already done. */
    void join(ServerPlayer player, String core, long now) {
        Member m = members.get(player.getUUID());
        if (m != null && m.player != player) {
            leave(player.getUUID(), now); // logged in again: a new player object
            m = null;
        }
        if (m == null) {
            m = new Member(player);
            members.put(player.getUUID(), m);
        }
        if (refCore == null) refCore = core;
        m.core = core;
        if (m.synced) {
            m.synced = false; // joining again: start over
            updateHeld(now);
        }
        m.waiting = true;
        serveWaiting(now, false);
    }

    void leave(UUID player, long now) {
        Member m = members.remove(player);
        if (m == null) return;
        uploads.remove(player);
        if (player.equals(requestFrom)) {
            requestId = -1;
            serveWaiting(now, true);
        }
        if (syncedCount() == 0) timeline.setWatched(false, now);
        updateHeld(now);
        broadcastState(now);
    }

    int syncedCount() {
        int n = 0;
        for (Member m : members.values()) {
            if (m.synced) n++;
        }
        return n;
    }

    /** Whoever's savestates count: Player 1, else Player 2 (D85); with no pad held, the longest-watching client. */
    @Nullable
    private Member source() {
        for (UUID holder : heldBy) {
            if (holder != null) return members.get(holder);
        }
        for (Member m : members.values()) {
            if (m.synced) return m;
        }
        return null;
    }

    @Nullable
    UUID sourceId() {
        Member source = source();
        return source != null ? source.player.getUUID() : null;
    }

    private boolean isHolder(UUID player) {
        for (UUID holder : heldBy) {
            if (player.equals(holder)) return true;
        }
        return false;
    }

    /** Matches the ports to the console's pad holders; only clients that are in the game can hold one. */
    void updateHeld(long now) {
        GameHost console = host.get(); // null: a handheld put away, so nobody plays it any more
        boolean changed = false;
        List<Segment> out = new ArrayList<>();
        for (int p = 0; p < heldBy.length; p++) {
            UUID holder = console != null ? console.holder(p) : null;
            Member m = holder == null ? null : members.get(holder);
            UUID want = m != null && m.synced ? holder : null;
            if (Objects.equals(want, heldBy[p])) continue;
            if (!changed) flushSpectators(now); // roles change: nobody may miss a batch
            changed = true;
            if (heldBy[p] != null) out.addAll(timeline.release(p, now));
            heldBy[p] = want;
            if (want != null) out.addAll(timeline.take(p, now, lead(m)));
        }
        if (!changed) return;
        publish(out, Map.of(), now);
        broadcastState(now);
    }

    /** How far ahead a take or an event lands: past the slowest player's round trip, plus a little (D12). */
    private int lead(@Nullable Member extra) {
        int rtt = extra != null ? extra.rttMs : 0;
        for (UUID holder : heldBy) {
            Member m = holder == null ? null : members.get(holder);
            if (m != null) rtt = Math.max(rtt, m.rttMs);
        }
        double frameMs = 1000.0 / timeline.fps();
        return (int) Math.ceil(rtt / 2.0 / frameMs) + 2;
    }

    // --- Inputs and events ------------------------------------------------------------------------------------------------

    void onInputs(ServerPlayer player, int port, int start, int[] buttons, long now) {
        Member m = members.get(player.getUUID());
        if (m == null || !m.synced || port < 0 || port >= heldBy.length || !player.getUUID().equals(heldBy[port])) return;
        int horizon = (int) Math.ceil(timeline.present(now)) + 4 * window() + 120;
        if (start + buttons.length > horizon) return; // far in the future: not a real client
        Segment accepted = timeline.accept(port, start, buttons);
        if (accepted == null) return;
        List<Segment> out = new ArrayList<>();
        out.add(accepted);
        out.addAll(timeline.fillUnheld());
        publish(out, Map.of(), now);
    }

    /** RESET or a disk swap, stamped onto a frame everyone reaches after hearing of it. */
    void schedule(int event, long now) {
        int status = timeline.status();
        int frame = timeline.schedule(event, now, lead(null));
        publish(List.of(), Map.of(frame, event), now);
        if (timeline.status() != status) broadcastState(now);
    }

    /** Holders hear everything at once; spectators get a batch every tick (they run a few frames behind anyway, D87). */
    private void publish(List<Segment> segments, Map<Integer, Integer> events, long now) {
        if (segments.isEmpty() && events.isEmpty()) return;
        SessionInputsPayload payload = inputs(segments, events, now);
        for (Member m : members.values()) {
            if (m.synced && isHolder(m.player.getUUID())) send(m, payload);
        }
        spectatorSegments.addAll(segments);
        events.forEach((f, e) -> spectatorEvents.merge(f, e, (a, b) -> a | b));
    }

    private void flushSpectators(long now) {
        if (spectatorSegments.isEmpty() && spectatorEvents.isEmpty()) return;
        SessionInputsPayload payload = inputs(merge(spectatorSegments), spectatorEvents, now);
        for (Member m : members.values()) {
            if (m.synced && !isHolder(m.player.getUUID())) send(m, payload);
        }
        spectatorSegments.clear();
        spectatorEvents.clear();
    }

    private SessionInputsPayload inputs(List<Segment> segments, Map<Integer, Integer> events, long now) {
        int[] frames = new int[events.size()], flags = new int[events.size()];
        int i = 0;
        for (Map.Entry<Integer, Integer> e : events.entrySet()) {
            frames[i] = e.getKey();
            flags[i++] = e.getValue();
        }
        return new SessionInputsPayload(key, bootId, timeline.present(now), List.copyOf(segments), frames, flags);
    }

    /** Joins each port's back-to-back runs into one. */
    static List<Segment> merge(List<Segment> segments) {
        List<Segment> out = new ArrayList<>();
        for (int p = 0; p < ConsoleKind.MAX_PORTS; p++) {
            Segment run = null;
            for (Segment s : segments) {
                if (s.port() != p) continue;
                if (run != null && run.end() == s.start()) {
                    int[] joined = Arrays.copyOf(run.buttons(), run.buttons().length + s.buttons().length);
                    System.arraycopy(s.buttons(), 0, joined, run.buttons().length, s.buttons().length);
                    run = new Segment(p, run.start(), joined);
                } else {
                    if (run != null) out.add(run);
                    run = s;
                }
            }
            if (run != null) out.add(run);
        }
        return out;
    }

    // --- Savestates: joining, resyncs and resume (D12, D90) -----------------------------------------------------------

    /**
     * Serves clients waiting to join, from the stored savestate; if it's too old and someone can send a newer one,
     * asks for that first ({@code anyway}: don't ask, serve what there is).
     */
    private void serveWaiting(long now, boolean anyway) {
        boolean anyWaiting = members.values().stream().anyMatch(m -> m.waiting);
        if (!anyWaiting) return;
        Member source = source();
        if (!anyway && source != null && timeline.confirmed() + 1 - timeline.base() > FRESH_FRAMES) {
            requestState(source, now);
            return;
        }
        if (requestId >= 0 && !anyway) return; // a fresh one is on its way
        for (Member m : List.copyOf(members.values())) {
            if (m.waiting) sync(m, now);
        }
    }

    private void requestState(Member source, long now) {
        if (requestId >= 0) return;
        requestId = ++nextRequestId;
        requestFrom = source.player.getUUID();
        requestSent = now;
        send(source, new StateRequestPayload(key, bootId, requestId));
    }

    /** Sends a member the base savestate and every input since, then relays to them. */
    private void sync(Member m, long now) {
        int base = timeline.base();
        if (baseState == null) {
            send(m, new SessionSyncPayload(key, bootId, base, 0, 0, new byte[0], bootSram != null ? bootSram : new byte[0]));
        } else {
            for (int off = 0; off < baseState.length; off += SessionSyncPayload.MAX_CHUNK) {
                int n = Math.min(SessionSyncPayload.MAX_CHUNK, baseState.length - off);
                send(m, new SessionSyncPayload(key, bootId, base, baseState.length, off, Arrays.copyOfRange(baseState, off, off + n),
                        new byte[0]));
            }
        }
        // The log since the base, in pieces; the batch spectators haven't had yet is in it already.
        for (Segment s : timeline.segmentsFrom(base)) {
            for (int off = 0; off < s.buttons().length; off += SYNC_SEGMENT_FRAMES) {
                int n = Math.min(SYNC_SEGMENT_FRAMES, s.buttons().length - off);
                send(m, inputs(List.of(new Segment(s.port(), s.start() + off,
                        Arrays.copyOfRange(s.buttons(), off, off + n))), Map.of(), now));
            }
        }
        for (Map.Entry<Integer, byte[]> load : loads.tailMap(base, true).entrySet()) sendLoad(m, load.getKey(), load.getValue());
        send(m, inputs(List.of(), timeline.eventsFrom(base), now));
        boolean first = syncedCount() == 0;
        m.synced = true;
        m.waiting = false;
        m.checks.clear();
        if (first) timeline.setWatched(true, now);
        updateHeld(now);
        broadcastState(now);
    }

    void onStateUpload(ServerPlayer player, StateUploadPayload p, long now) {
        UUID id = player.getUUID();
        Member m = members.get(id);
        if (m == null || !m.synced) return;
        if (p.requestId() != StateUploadPayload.LEAVING && p.requestId() != requestId) return;
        Upload upload = uploads.get(id);
        if (p.offset() == 0) {
            if (p.total() <= 0 || p.total() > MAX_STATE) return;
            upload = new Upload(p.requestId(), p.frame(), p.total(), p.thumbnail());
            uploads.put(id, upload);
        }
        if (upload == null || upload.requestId != p.requestId() || p.offset() != upload.received
                || upload.received + p.data().length > upload.data.length) {
            uploads.remove(id);
            return;
        }
        System.arraycopy(p.data(), 0, upload.data, upload.received, p.data().length);
        upload.received += p.data().length;
        if (upload.received < upload.data.length) return;
        uploads.remove(id);
        if (upload.requestId == requestId) requestId = -1;
        finishCardSaves(upload.data, m.core, upload.thumbnail);
        adopt(upload.frame, upload.data, m.core, now);
    }

    /** A new base: the newest savestate. It's also the resume snapshot (D90). */
    private void adopt(int frame, byte[] state, String core, long now) {
        if (frame >= timeline.base() && frame <= timeline.confirmed() + 1) {
            baseState = state;
            baseCore = core;
            timeline.rebase(frame);
            loads.headMap(frame).clear();
            lastSnapshot = now;
            writeResume();
        }
        serveWaiting(now, true);
    }

    private void writeResume() {
        if (baseState == null || baseCore == null) return;
        try {
            ResumeStore.write(resumeFile, new ResumeStore.Resume(bootId, cartId, timeline.base(), timeline.everHeld(),
                    baseCore, baseState));
        } catch (IOException e) {
            Cartridge.LOGGER.warn("Couldn't save the resume snapshot {}", resumeFile, e);
        }
    }

    // --- Memory Cards (D91-D94) ------------------------------------------------------------------------------------------

    /** Saves the game onto the card in the console: the next savestate from the authority goes onto it. */
    int saveCard(ServerPlayer player, ItemStack card, long now) {
        Member source = source();
        if (source == null) return ConsoleMenu.RESULT_NO_GAME;
        cardSaves.add(new CardSave(player, card.copy(), now));
        requestState(source, now); // or the one already on its way
        return ConsoleMenu.RESULT_SAVING;
    }

    private void finishCardSaves(byte[] state, String core, byte[] thumbnail) {
        if (cardSaves.isEmpty()) return;
        GameHost console = host.get();
        CartridgeContents cart = console != null ? console.contents() : null;
        SaveStore store = SaveServer.store(server);
        for (CardSave save : cardSaves) {
            int result;
            if (console == null || cart == null || !ItemStack.isSameItemSameComponents(console.memoryCard(), save.card())) {
                result = ConsoleMenu.RESULT_CARD_GONE;
            } else {
                ItemStack card = console.memoryCard().copy();
                MemoryCardContents old = CartridgeComponents.MEMORY_CARD.get(card);
                UUID id = old != null ? old.cardId() : UUID.randomUUID();
                long savedAt = System.currentTimeMillis();
                try {
                    store.writeCard(id, new SaveStore.CardState(cart.romSha1(), core, savedAt, state));
                    CartridgeComponents.MEMORY_CARD.set(card, new MemoryCardContents(id, cart.title(),
                            cart.romSha1(), savedAt, thumbnail.length == MemoryCardContents.THUMB_BYTES ? thumbnail : new byte[0]));
                    console.setMemoryCard(card);
                    result = ConsoleMenu.RESULT_SAVED;
                } catch (IOException e) {
                    Cartridge.LOGGER.error("Couldn't write Memory Card {}", id, e);
                    result = ConsoleMenu.RESULT_FAILED;
                }
            }
            ConsoleMenu.showResult(save.player(), key, result);
        }
        cardSaves.clear();
    }

    /**
     * Loads a card's savestate (D92): it replaces the game's state on a frame everyone reaches after hearing of it,
     * like RESET. While paused, the free ports run on to that frame, so everyone sees the loaded picture at once.
     */
    int loadCard(SaveStore.CardState card, long now) {
        if (syncedCount() == 0) return ConsoleMenu.RESULT_NO_GAME;
        if (refCore != null && !refCore.equals(card.core())) return ConsoleMenu.RESULT_CORE; // D89
        int frame = timeline.schedule(SessionTimeline.EVENT_LOAD, now, lead(null));
        loads.put(frame, card.state());
        for (Member m : members.values()) {
            if (m.synced) sendLoad(m, frame, card.state());
        }
        publish(timeline.fillPausedTo(frame + 1), Map.of(frame, SessionTimeline.EVENT_LOAD), now);
        return ConsoleMenu.RESULT_LOADED;
    }

    private void sendLoad(Member m, int frame, byte[] state) {
        for (int off = 0; off < state.length; off += SessionLoadPayload.MAX_CHUNK) {
            int n = Math.min(SessionLoadPayload.MAX_CHUNK, state.length - off);
            send(m, new SessionLoadPayload(key, bootId, frame, state.length, off, Arrays.copyOfRange(state, off, off + n)));
        }
    }

    // --- Desyncs (D12) ----------------------------------------------------------------------------------------------------

    void onChecksum(ServerPlayer player, int frame, long checksum, long now) {
        Member m = members.get(player.getUUID());
        Member source = source();
        if (m == null || !m.synced || source == null) return;
        if (m == source) {
            truth.put(frame, checksum);
            while (truth.size() > KEEP_CHECKSUMS) truth.pollFirstEntry();
            for (Member other : List.copyOf(members.values())) {
                if (other != source) compare(other, frame, now);
            }
        } else {
            m.checks.put(frame, checksum);
            while (m.checks.size() > KEEP_CHECKSUMS) m.checks.pollFirstEntry();
            compare(m, frame, now);
        }
    }

    private void compare(Member m, int frame, long now) {
        Long expected = truth.get(frame);
        Long actual = expected == null ? null : m.checks.remove(frame);
        if (actual == null || actual.equals(expected) || !m.synced) return;
        if (now - m.lastResync < RESYNC_INTERVAL) return;
        m.lastResync = now;
        Cartridge.LOGGER.info("{} desynced from the game at {} (frame {}): resyncing", m.player.getGameProfile().getName(),
                key.describe(), frame);
        m.synced = false;
        m.waiting = true;
        updateHeld(now);
        serveWaiting(now, false);
    }

    // --- Clock and housekeeping -------------------------------------------------------------------------------------------

    void onPing(ServerPlayer player, int nonce, int rttMs, long now) {
        Member m = members.get(player.getUUID());
        if (m == null) return;
        m.rttMs = Math.clamp(rttMs, 0, 5000);
        send(m, new SessionPongPayload(key, bootId, nonce, timeline.present(now)));
    }

    void tick(long now) {
        if (lastTick != 0 && now - lastTick > 250_000_000L) {
            timeline.skipTime(now - lastTick - 50_000_000L); // the server stood still (a paused singleplayer game)
        }
        lastTick = now;
        // Gone without saying so: logged off, changed dimension, or far beyond any TV (clients leave at the radius).
        // While a handheld is put away its members stay a little longer: they're uploading where it got to (D164).
        GameHost console = host.get();
        List<UUID> gone = new ArrayList<>();
        for (Member m : members.values()) {
            ServerPlayer p = m.player;
            boolean far = console != null && (p.level() != console.serverLevel()
                    || p.distanceToSqr(console.where()) > console.reach() * console.reach());
            if (p.isRemoved() || p.hasDisconnected() || far) gone.add(p.getUUID());
        }
        gone.forEach(id -> leave(id, now));
        publish(timeline.fillByClock(now), Map.of(), now);
        timeline.clamp(now, window());
        flushSpectators(now);

        if (requestId >= 0 && now - requestSent > REQUEST_TIMEOUT) {
            requestId = -1;
            serveWaiting(now, true);
        }
        Member source = source();
        if (!cardSaves.isEmpty()) {
            if (now - cardSaves.get(0).requested() > 2 * REQUEST_TIMEOUT || source == null) {
                for (CardSave save : cardSaves) ConsoleMenu.showResult(save.player(), key, ConsoleMenu.RESULT_FAILED);
                cardSaves.clear();
            } else {
                requestState(source, now); // the last request went unanswered
            }
        }
        long interval = CartridgeConfig.server().resumeSnapshotSeconds * 1_000_000_000L;
        if (source != null && (now - lastSnapshot > interval || timeline.confirmed() - timeline.base() > MAX_LOG_FRAMES)) {
            lastSnapshot = now;
            requestState(source, now);
        }
        if (now - lastStateSent > STATE_INTERVAL) broadcastState(now);
    }

    private void broadcastState(long now) {
        lastStateSent = now;
        int[] starts = new int[heldBy.length], rtts = new int[heldBy.length];
        for (int p = 0; p < heldBy.length; p++) {
            starts[p] = heldBy[p] != null ? timeline.start(p) : 0;
            Member m = heldBy[p] != null ? members.get(heldBy[p]) : null;
            rtts[p] = m != null ? m.rttMs : 0;
        }
        SessionStatePayload state = new SessionStatePayload(key, bootId, timeline.status(), timeline.present(now),
                syncedCount(), heldBy.clone(), starts, rtts, sourceId());
        for (Member m : members.values()) {
            if (m.synced) send(m, state);
        }
    }

    /** The game ended (power, cartridge, block gone) or the chunk unloaded; {@code forget} drops the resume file. */
    void close(boolean forget) {
        if (forget) ResumeStore.delete(resumeFile);
        for (CardSave save : cardSaves) ConsoleMenu.showResult(save.player(), key, ConsoleMenu.RESULT_NO_GAME);
        cardSaves.clear();
        members.clear();
    }

    void sendCoreMismatch(ServerPlayer player) {
        Services.NETWORK.sendToPlayer(player, new CoreMismatchPayload(key, bootId));
    }

    private static int window() {
        return CartridgeConfig.server().rollbackFrames;
    }

    private static void send(Member m, CustomPacketPayload payload) {
        if (!m.player.hasDisconnected()) Services.NETWORK.sendToPlayer(m.player, payload);
    }
}
