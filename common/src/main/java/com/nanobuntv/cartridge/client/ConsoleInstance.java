package com.nanobuntv.cartridge.client;

import com.nanobuntv.cartridge.Cartridge;
import com.nanobuntv.cartridge.config.CartridgeConfig;
import com.nanobuntv.cartridge.emu.ConsoleKind;
import com.nanobuntv.cartridge.emu.CoreSpec;
import com.nanobuntv.cartridge.netplay.GameKey;
import com.nanobuntv.cartridge.netplay.LinkedSaves;
import com.nanobuntv.cartridge.emu.HostPlatform;
import com.nanobuntv.cartridge.emu.HostProcess;
import com.nanobuntv.cartridge.emu.MgbaClock;
import com.nanobuntv.cartridge.emu.Mouse;
import com.nanobuntv.cartridge.emu.Natives;
import com.nanobuntv.cartridge.emu.RomCache;
import com.nanobuntv.cartridge.emu.SharedMemory;
import com.nanobuntv.cartridge.emu.States;
import com.nanobuntv.cartridge.item.CartridgeContents;
import com.nanobuntv.cartridge.item.MemoryCardContents;
import com.nanobuntv.cartridge.net.ChecksumPayload;
import com.nanobuntv.cartridge.net.CoreMismatchPayload;
import com.nanobuntv.cartridge.net.InputFramesPayload;
import com.nanobuntv.cartridge.net.RomUploadStartPayload;
import com.nanobuntv.cartridge.net.SessionInputsPayload;
import com.nanobuntv.cartridge.net.SessionJoinPayload;
import com.nanobuntv.cartridge.net.SessionLeavePayload;
import com.nanobuntv.cartridge.net.SessionLoadPayload;
import com.nanobuntv.cartridge.net.SessionPingPayload;
import com.nanobuntv.cartridge.net.SessionPongPayload;
import com.nanobuntv.cartridge.net.SessionStatePayload;
import com.nanobuntv.cartridge.net.SessionSyncPayload;
import com.nanobuntv.cartridge.net.SramUploadPayload;
import com.nanobuntv.cartridge.net.StateRequestPayload;
import com.nanobuntv.cartridge.net.StateUploadPayload;
import com.nanobuntv.cartridge.netplay.RollbackEngine;
import com.nanobuntv.cartridge.netplay.Segment;
import com.nanobuntv.cartridge.netplay.SessionTimeline;
import com.nanobuntv.cartridge.platform.Services;
import com.nanobuntv.cartridge.rom.RomFormats;
import java.io.EOFException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicIntegerArray;
import java.util.concurrent.locks.LockSupport;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import org.jetbrains.annotations.Nullable;

/**
 * One running console on this client (D7, D13): its own thread owns a cartridge-host process, boots the cartridge,
 * joins the console's networked game on the server (D12) and runs it through a {@link RollbackEngine}: as a player
 * on the present frame, guessing the other player's inputs, or as a spectator a few frames behind the confirmed ones
 * (D87). The render thread reads frames straight from {@link #sharedMemory()}, sets inputs and hands in the server's
 * messages; everything else happens here, so a slow download or a dying host never stalls the game. Started by
 * {@link ConsoleView}; one instance per power-on that this client sees.
 * <p>
 * Saves (D24): a game from power-on starts with the cartridge's battery save from the server. The authority's client
 * (D85) watches the battery RAM and uploads it once it settles after a change, and when it stops (D95). Memory Card
 * loads arrive as savestates that replace the game's state on a frame (D92).
 */
final class ConsoleInstance {
    enum Status { STARTING, DOWNLOADING_ROM, SYNCING, RUNNING, ERROR, STOPPED }


    private static final int MAX_CATCH_UP = 6;
    /** The most input delay lockstep may use (D189): a 250 ms round trip. */
    private static final int LOCKSTEP_MAX_DELAY = 15;
    /** A console that runs by speed rolls back if re-running the window takes at most this share of a frame (D196). */
    private static final double ROLLBACK_SHARE = 0.6;
    /** Further behind than this, a player catches up silently through the confirmed frames. */
    private static final int FAST_FORWARD_FRAMES = 30;
    /** How far a player may run past frames whose inputs nobody holding a pad has sent yet (a take or an event). */
    private static final int MAX_UNCONFIRMED = 120;
    /** Spectators keep about this many confirmed frames in hand (D87). */
    private static final int SPECTATOR_BUFFER = 4;
    private static final long PING_INTERVAL = 1_000_000_000L;
    private static final long JOIN_RETRY = 10_000_000_000L;
    private static final long WAITING_AFTER = 1_000_000_000L;
    /** A battery RAM change is uploaded once it has stayed the same this many frames (D95). */
    private static final int SRAM_QUIET_FRAMES = 120;
    private static final Object SETUP_LOCK = new Object();
    private static final AtomicInteger COUNTER = new AtomicInteger();

    private record Received(CustomPacketPayload payload, long nanos) {}

    private final CartridgeContents cart;
    private final Path gameDir;
    private final GameKey game;
    private final ConsoleKind kind;
    private final Map<String, String> playerOptions;
    /** A linked game's two cartridges, side 1 then side 2 (D177); null for one game. */
    @Nullable
    private final List<CartridgeContents> linked;
    private final int bootId;
    private final UUID me;
    private final int ports;
    private final AtomicIntegerArray inputs = new AtomicIntegerArray(ConsoleKind.MAX_PORTS);
    private final int[] mouseDx = new int[ConsoleKind.MAX_PORTS], mouseDy = new int[ConsoleKind.MAX_PORTS];
    private final ConcurrentLinkedQueue<Received> inbox = new ConcurrentLinkedQueue<>();
    private final TvAudio audio = new TvAudio();
    private final Thread thread;
    /** The running host, for {@link #stop()}'s watchdog. */
    private volatile HostProcess liveHost;
    private static final long STOP_TIMEOUT_MILLIS = 5000;

    private volatile Status status = Status.STARTING;
    private volatile Component error;
    private volatile SharedMemory shm;
    private volatile boolean paused, stopRequested, pulled, leavingRange;
    /** The SHA-1 of the ROM or BIOS being downloaded, for the TV's progress bar (D70). */
    private volatile String downloading;
    /** The networked game's state as last heard (D85, D88), and who the game waits for (D84). */
    private volatile boolean sessionPaused;
    private volatile UUID waitingFor;

    // Emulator thread only, during a session.
    private SessionStatePayload state;
    private SessionClock clock;
    private long rttNanos = 100_000_000L, pingSent;
    private int pingNonce, delay, delayVotes, delayFrame = -1;
    private double fps = 60.0988;
    /** Lockstep (D189) rather than rollback: the console's way, or for one decided by speed, this PC's (D196). */
    private boolean lockstep;
    private double spectatorCredit;
    private long lastStep, stalledSince;
    /** Memory Card savestates arriving in pieces, by the frame they load on (D92). */
    private final Map<Integer, byte[]> loading = new HashMap<>();
    private final Map<Integer, Integer> loadingReceived = new HashMap<>();
    /** The battery RAM the server has (as far as this client knows), and a change waiting to settle (D95). */
    private byte[] sramSent, sramPending;
    private int sramPendingFrame;
    private ByteBuffer thumbnailFrame;

    /**
     * {@code playerOptions}: core options that only change what this player sees, never the game (the Pocketron's
     * screen colours, D165).
     */
    ConsoleInstance(ConsoleKind kind, CartridgeContents cart, Path gameDir, GameKey game, int bootId, UUID me,
            Map<String, String> playerOptions, @Nullable List<CartridgeContents> linked) {
        this.linked = linked == null ? null : List.copyOf(linked);
        this.kind = kind;
        this.ports = kind.ports();
        this.cart = cart;
        this.gameDir = gameDir;
        this.game = game;
        this.playerOptions = playerOptions;
        this.bootId = bootId;
        this.me = me;
        this.thread = new Thread(this::run, "Cartridge console " + COUNTER.incrementAndGet());
        thread.setDaemon(true);
    }

    void start() {
        thread.start();
    }

    CartridgeContents cart() {
        return cart;
    }

    int bootId() {
        return bootId;
    }

    Status status() {
        return status;
    }

    Component error() {
        return error;
    }

    /** Null until the host is up. */
    SharedMemory sharedMemory() {
        return status == Status.RUNNING ? shm : null;
    }

    TvAudio audio() {
        return audio;
    }

    /**
     * A port's input: a joypad bitmask (bit n = RETRO_DEVICE_ID_JOYPAD_n), a mouse's buttons (D144, {@link Mouse}), or a
     * Pixelon 64 Pad's buttons and stick ({@link com.nanobuntv.cartridge.emu.N64Pad}).
     */
    void setInput(int port, int buttons) {
        inputs.set(port, buttons);
    }

    /**
     * Mouse movement for a port (D144), in the game's picture pixels: it goes out over the next frames, as much as a
     * frame carries each ({@link Mouse}).
     */
    synchronized void addMouseMotion(int port, int dx, int dy) {
        mouseDx[port] += dx;
        mouseDy[port] += dy;
    }

    /** The next frame's share of the movement, as {@link Mouse} bits (none for a pad). */
    private synchronized int takeMouseMotion(int port) {
        int dx = Mouse.clamp(mouseDx[port]), dy = Mouse.clamp(mouseDy[port]);
        mouseDx[port] -= dx;
        mouseDy[port] -= dy;
        return Mouse.encode(dx, dy, false, false);
    }

    void setPaused(boolean paused) {
        this.paused = paused;
        audio.setPaused(paused);
    }

    /** The SHA-1 being downloaded while {@link Status#DOWNLOADING_ROM}, else null. */
    String downloading() {
        return downloading;
    }

    /** Nobody holds a pad, so the game stands still (D85, D88). */
    boolean sessionPaused() {
        return sessionPaused && status == Status.RUNNING;
    }

    /** The player the game has been waiting for for a while (D84), or null. */
    UUID waitingFor() {
        return waitingFor;
    }

    /** A message from the server about this console's game; render thread. */
    void deliver(CustomPacketPayload payload) {
        inbox.add(new Received(payload, System.nanoTime()));
        LockSupport.unpark(thread);
    }

    void stop() {
        stopRequested = true;
        LockSupport.unpark(thread);
        // A host that hangs (a core stuck in a frame) never lets the thread see the request: kill it after a while.
        Thread watchdog = new Thread(() -> {
            try {
                thread.join(STOP_TIMEOUT_MILLIS);
            } catch (InterruptedException e) {
                return;
            }
            HostProcess h = liveHost;
            if (thread.isAlive() && h != null) {
                Cartridge.LOGGER.warn("cartridge-host at {} stopped answering; killing it", game.describe());
                h.kill();
            }
        }, thread.getName() + " watchdog");
        watchdog.setDaemon(true);
        watchdog.start();
    }

    /** Out of range (D13): stop, and if nobody else is watching, leave the server a savestate to resume from (D90). */
    void leaveRange() {
        leavingRange = true;
        stop();
    }

    /** The cartridge was pulled out while running (D30): stop with a buzz. The picture glitch is ConsoleView's. */
    void pull() {
        pulled = true;
        stop();
    }

    private void run() {
        HostProcess host = null;
        try {
            HostPlatform platform = HostPlatform.current().orElse(null);
            if (platform == null) {
                fail(Component.literal(HostPlatform.unsupportedMessage()));
                return;
            }
            byte[] rom = rom(cart);
            if (rom == null) return;
            byte[][] linkedRoms = null;
            if (linked != null) {
                linkedRoms = new byte[2][];
                for (int i = 0; i < 2; i++) {
                    linkedRoms[i] = rom(linked.get(i));
                    if (linkedRoms[i] == null) return;
                }
            }
            Optional<RomFormats.Bios> bios = RomFormats.bios(cart.system());
            if (bios.isPresent() && !RomDownloads.hasBios(gameDir, bios.get())
                    && download(RomUploadStartPayload.KIND_BIOS, bios.get().sha1(), "cartridge_mod.tv.bios_missing") == null) {
                return;
            }
            if (!kind.takes(cart.system())) {
                fail(Component.translatable("cartridge_mod.tv.unknown_system", cart.system()));
                return;
            }
            Path hostExe, core;
            synchronized (SETUP_LOCK) {
                hostExe = Natives.extractHost(gameDir, platform);
                core = Natives.extractCore(gameDir, platform, kind.core()); // bundled and pinned (D114, D137)
            }
            if (stopRequested) return;

            Path shmDir = SharedMemory.directory(gameDir);
            host = HostProcess.start(hostExe, shmDir.resolve("console-" + ProcessHandle.current().pid() + "-"
                    + COUNTER.incrementAndGet() + SharedMemory.FILE_SUFFIX), gameDir.resolve("cartridge_mod").resolve("system"));
            liveHost = host;
            host.loadCore(core);
            for (var option : kind.options().entrySet()) host.setOption(option.getKey(), option.getValue());
            for (var option : playerOptions.entrySet()) host.setOption(option.getKey(), option.getValue());
            HostProcess.AvInfo av = linkedRoms == null ? host.loadGame(kind.gameFileName(cart.system()), rom)
                    : host.loadGameLinked(HostProcess.SUBSYSTEM_GAME_BOY_LINK, new String[] {
                            kind.gameFileName(linked.get(0).system()), kind.gameFileName(linked.get(1).system())},
                            linkedRoms);
            if (kind.core() == CoreSpec.MGBA) MgbaClock.useEmulatedTime(host); // the same clock for everyone (D186)
            if (kind.checksum() == ConsoleKind.Checksum.PICTURE) host.setChecksum(HostProcess.CHECKSUM_PICTURE); // D194
            if (kind.bootFrames() > 0) { // the core's own start-up, the same on every client
                host.run(kind.bootFrames(), HostProcess.RUN_MUTE | HostProcess.RUN_NO_VIDEO, ports,
                        new int[kind.bootFrames() * ports]);
            }
            shm = host.sharedMemory();
            session(host, av, kind.core().version());
        } catch (EOFException e) {
            Cartridge.LOGGER.warn("cartridge-host exited unexpectedly", e);
            fail(Component.translatable("cartridge_mod.tv.crashed"));
        } catch (IOException e) {
            Cartridge.LOGGER.warn("Console failed", e);
            fail(Component.translatable("cartridge_mod.tv.error", String.valueOf(e.getMessage())));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Throwable t) {
            Cartridge.LOGGER.error("Console failed", t);
            fail(Component.translatable("cartridge_mod.tv.error", t.toString()));
        } finally {
            send(new SessionLeavePayload(game, bootId));
            if (pulled) buzz();
            audio.close();
            if (host != null) host.close();
            liveHost = null;
            if (status != Status.ERROR) status = Status.STOPPED;
        }
    }

    /** Fetches a file from the server (D16); null (and the TV shows {@code missingKey}) if it doesn't have it. */
    /** A game's ROM, from the cache or else downloaded from the server; null if it couldn't be had. */
    @Nullable
    private byte[] rom(CartridgeContents game) throws IOException, InterruptedException {
        byte[] rom = RomCache.get(gameDir, game.romSha1()).orElse(null);
        return rom != null ? rom : download(RomUploadStartPayload.KIND_ROM, game.romSha1(), "cartridge_mod.tv.rom_missing");
    }

    private byte[] download(int kind, String sha1, String missingKey) throws IOException, InterruptedException {
        status = Status.DOWNLOADING_ROM;
        downloading = sha1;
        try {
            return RomDownloads.await(kind, sha1, () -> stopRequested);
        } catch (RomDownloads.MissingException e) {
            fail(Component.translatable(missingKey));
            return null;
        } finally {
            downloading = null;
            if (status == Status.DOWNLOADING_ROM) status = Status.STARTING;
        }
    }

    // --- The networked game (D12) -------------------------------------------------------------------------------------

    private void session(HostProcess host, HostProcess.AvInfo av, String coreVersion) throws IOException {
        RollbackEngine.Emulator emu = emulator(host);
        byte[] bootState = emu.serialize(); // the power-on state: a sync from frame 0 starts here
        fps = av.fps();
        lockstep = switch (kind.netplay()) {
            case ROLLBACK -> false;
            case LOCKSTEP -> true;
            case BY_SPEED -> !rollsBackInTime(host, emu, bootState);
        };
        status = Status.SYNCING;
        state = null;
        sessionPaused = false;
        clock = new SessionClock(av.fps());
        RollbackEngine engine = null;
        byte[] syncState = null;
        int syncBase = 0, syncReceived = 0;
        long joined = System.nanoTime();
        send(new SessionJoinPayload(game, bootId, coreVersion, av.fps()));

        short[] audioBuffer = new short[16384 * 2];
        long audioPos = shm.audioPosition();
        lastStep = System.nanoTime();

        while (!stopRequested) {
            long now = System.nanoTime();
            for (Received r; (r = inbox.poll()) != null; ) {
                CustomPacketPayload payload = r.payload();
                if (payload instanceof SessionSyncPayload s) {
                    if (s.offset() == 0) {
                        engine = null; // (re)starting from the server's savestate
                        status = Status.SYNCING;
                        syncBase = s.baseFrame();
                        syncState = s.total() > 0 ? new byte[s.total()] : null;
                        syncReceived = 0;
                    }
                    if (syncState != null && s.offset() == syncReceived && syncReceived + s.data().length <= syncState.length) {
                        System.arraycopy(s.data(), 0, syncState, syncReceived, s.data().length);
                        syncReceived += s.data().length;
                    }
                    if (syncState == null || syncReceived == syncState.length) {
                        if (syncState != null) syncState = fromNetwork(syncState);
                        emu.unserialize(syncState != null ? syncState : bootState);
                        if (syncState != null) {
                            // A saved game shows where it was even while paused (a handheld taken out again, D164):
                            // one frame drawn silently, then the savestate loaded again, so the game itself is untouched.
                            host.run(1, HostProcess.RUN_MUTE, ports, new int[ports]);
                            emu.unserialize(syncState);
                        }
                        if (syncState == null) sramSent = loadSram(host, s.sram()); // from power-on: the cart's save (D24)
                        engine = new RollbackEngine(emu, syncBase, ports);
                        syncState = null;
                        loading.clear();
                        loadingReceived.clear();
                        if (state != null) applyHolder(engine);
                    }
                } else if (payload instanceof SessionInputsPayload in && engine != null) {
                    clock.sample(in.present(), r.nanos(), rttNanos, now);
                    boolean ok = true;
                    for (Segment seg : in.segments()) ok &= engine.addInputs(seg.port(), seg.start(), seg.buttons());
                    for (int i = 0; i < in.eventFrames().length; i++) engine.addEvent(in.eventFrames()[i], in.eventFlags()[i]);
                    if (!ok) engine = rejoin(coreVersion, av.fps());
                } else if (payload instanceof SessionLoadPayload load && engine != null) {
                    receiveLoad(engine, load);
                } else if (payload instanceof SessionStatePayload st) {
                    state = st;
                    sessionPaused = st.status() == SessionTimeline.PAUSED;
                    clock.sample(st.present(), r.nanos(), rttNanos, now);
                    if (engine != null) applyHolder(engine);
                } else if (payload instanceof StateRequestPayload rq && engine != null) {
                    uploadState(engine, rq.requestId());
                } else if (payload instanceof SessionPongPayload pong && pong.nonce() == pingNonce) {
                    long rtt = r.nanos() - pingSent;
                    rttNanos = (rttNanos * 3 + rtt) / 4;
                    clock.sample(pong.present(), r.nanos(), rttNanos, now);
                } else if (payload instanceof CoreMismatchPayload) {
                    fail(Component.translatable("cartridge_mod.tv.core_differs"));
                    return;
                }
            }

            if (engine == null) {
                if (now - joined > JOIN_RETRY && syncState == null) {
                    joined = now;
                    send(new SessionJoinPayload(game, bootId, coreVersion, av.fps()));
                }
                LockSupport.parkNanos(5_000_000L);
                continue;
            }
            status = Status.RUNNING;
            if (paused) {
                audio.idle();
                lastStep = now;
                LockSupport.parkNanos(10_000_000L);
                continue;
            }
            if (now - pingSent > PING_INTERVAL) {
                pingSent = now;
                send(new SessionPingPayload(game, bootId, ++pingNonce, (int) (rttNanos / 1_000_000L)));
            }

            int ran;
            try {
                engine.repair();
                ran = engine.myPort() >= 0 ? stepPlayer(engine, now) : stepSpectator(engine, now);
            } catch (RollbackEngine.DesyncException e) {
                Cartridge.LOGGER.warn("Console at {} lost track of the game ({}): joining again", game.describe(), e.getMessage());
                engine = rejoin(coreVersion, av.fps());
                continue;
            }
            for (long[] check : engine.takeFinalChecksums()) {
                send(new ChecksumPayload(game, bootId, (int) check[0], check[1]));
            }
            watchSram(engine);
            if (ran > 0) {
                long end = shm.audioPosition();
                long from = Math.max(audioPos, end - 16384);
                audioPos = shm.readAudio(from, audioBuffer);
                audio.push(audioBuffer, (int) (audioPos - from), av.sampleRate());
            } else {
                LockSupport.parkNanos(1_000_000L);
            }
        }
        // Stopping as the authority: the battery save as it is now (power off, a pulled cart, leaving; D95).
        if (engine != null && isSource()) {
            byte[] sram = saves(host);
            if (sram.length > 0 && !Arrays.equals(sram, sramSent)) uploadSram(sram);
        }
        // Leaving range as the last one watching, or a handheld put away by its authority: the server keeps where the
        // game was (D90, D164).
        if (leavingRange && engine != null && state != null && (state.members() <= 1 || game.isHandheld() && isSource())) {
            uploadState(engine, StateUploadPayload.LEAVING);
        }
    }

    // --- Saves (D24, D92, D95) ----------------------------------------------------------------------------------------

    private boolean isSource() {
        return state != null && me.equals(state.source());
    }

    /**
     * Puts the cartridge's battery save into the game before its first frame. A save of another size (from another
     * emulator, D97) is cut or padded to the game's; every client does the same, so they stay in step. Returns what
     * the game now has.
     */
    private byte[] loadSram(HostProcess host, byte[] save) throws IOException {
        if (linked == null) {
            byte[] current = host.getSram();
            if (save.length == 0 || current.length == 0) return current;
            byte[] fitted = Arrays.copyOf(save, current.length);
            host.setSram(fitted);
            return fitted;
        }
        // A linked game (D177): each Game Boy's own save, packed together (LinkedSaves).
        List<byte[]> saves = LinkedSaves.unpack(save, 2);
        for (int i = 0; i < 2 && saves != null; i++) {
            int memory = i == 0 ? HostProcess.MEMORY_GAME_BOY_1_SRAM : HostProcess.MEMORY_GAME_BOY_2_SRAM;
            byte[] current = host.getMemory(memory);
            if (saves.get(i).length > 0 && current.length > 0) host.setMemory(memory, Arrays.copyOf(saves.get(i), current.length));
        }
        return saves(host);
    }

    /** The battery save: the game's, or both Game Boys' of a linked game, packed (D177). */
    private byte[] saves(HostProcess host) throws IOException {
        if (linked == null) return host.getSram();
        byte[] first = host.getMemory(HostProcess.MEMORY_GAME_BOY_1_SRAM), second = host.getMemory(HostProcess.MEMORY_GAME_BOY_2_SRAM);
        return first.length == 0 && second.length == 0 ? new byte[0] : LinkedSaves.pack(List.of(first, second));
    }

    private void receiveLoad(RollbackEngine engine, SessionLoadPayload p) {
        if (p.offset() == 0) {
            loading.put(p.frame(), new byte[p.total()]);
            loadingReceived.put(p.frame(), 0);
        }
        byte[] data = loading.get(p.frame());
        int received = loadingReceived.getOrDefault(p.frame(), -1);
        if (data == null || p.offset() != received || received + p.data().length > data.length) {
            loading.remove(p.frame()); // a hole: the load event will find no state and resync
            loadingReceived.remove(p.frame());
            return;
        }
        System.arraycopy(p.data(), 0, data, received, p.data().length);
        received += p.data().length;
        if (received < data.length) {
            loadingReceived.put(p.frame(), received);
            return;
        }
        loading.remove(p.frame());
        loadingReceived.remove(p.frame());
        try {
            engine.addLoad(p.frame(), fromNetwork(data));
        } catch (IOException e) {
            Cartridge.LOGGER.warn("A Memory Card's savestate for {} didn't unpack", game.describe(), e); // the load resyncs
        }
    }

    /** The authority uploads the battery RAM once a change has settled (D95), from frames no late input can change. */
    private void watchSram(RollbackEngine engine) {
        boolean source = isSource();
        engine.trackSram(source);
        if (!source) {
            sramPending = null;
            sramSent = null; // another client keeps the server's copy now: what it has is unknown here
            return;
        }
        RollbackEngine.Snapshot sample = engine.takeFinalSram();
        if (sample == null || sample.state().length == 0) return;
        byte[] sram = sample.state();
        if (Arrays.equals(sram, sramSent)) {
            sramPending = null;
        } else if (!Arrays.equals(sram, sramPending)) {
            sramPending = sram;
            sramPendingFrame = sample.frame();
        } else if (sample.frame() - sramPendingFrame >= SRAM_QUIET_FRAMES) {
            uploadSram(sram);
        }
    }

    private void uploadSram(byte[] sram) {
        for (int off = 0; off < sram.length; off += SramUploadPayload.MAX_CHUNK) {
            int n = Math.min(SramUploadPayload.MAX_CHUNK, sram.length - off);
            send(new SramUploadPayload(game, bootId, sram.length, off, Arrays.copyOfRange(sram, off, off + n)));
        }
        sramSent = sram;
        sramPending = null;
    }

    /** The picture now, shrunk to a Memory Card's screenshot (D98): RGB565, big-endian; empty if there's none. */
    private byte[] thumbnail() {
        if (thumbnailFrame == null) thumbnailFrame = ByteBuffer.allocateDirect(640 * 480 * 4);
        SharedMemory.Frame f = shm == null ? null : shm.readFrame(thumbnailFrame);
        if (f == null || f.width() <= 0 || f.height() <= 0) return new byte[0];
        int w = MemoryCardContents.THUMB_WIDTH, h = MemoryCardContents.THUMB_HEIGHT;
        byte[] out = new byte[MemoryCardContents.THUMB_BYTES];
        for (int y = 0; y < h; y++) {
            int y0 = y * f.height() / h, y1 = Math.max(y0 + 1, (y + 1) * f.height() / h);
            for (int x = 0; x < w; x++) {
                int x0 = x * f.width() / w, x1 = Math.max(x0 + 1, (x + 1) * f.width() / w);
                int r = 0, g = 0, b = 0, n = 0;
                for (int sy = y0; sy < y1; sy++) {
                    for (int sx = x0; sx < x1; sx++) {
                        int i = (sy * f.width() + sx) * 4; // RGBA
                        r += thumbnailFrame.get(i) & 0xFF;
                        g += thumbnailFrame.get(i + 1) & 0xFF;
                        b += thumbnailFrame.get(i + 2) & 0xFF;
                        n++;
                    }
                }
                int rgb565 = (r / n >> 3) << 11 | (g / n >> 2) << 5 | b / n >> 3;
                out[(y * w + x) * 2] = (byte) (rgb565 >> 8);
                out[(y * w + x) * 2 + 1] = (byte) rgb565;
            }
        }
        return out;
    }

    private RollbackEngine rejoin(String coreVersion, double fps) {
        status = Status.SYNCING;
        send(new SessionJoinPayload(game, bootId, coreVersion, fps));
        return null;
    }

    private void applyHolder(RollbackEngine engine) {
        int port = -1;
        for (int p = 0; p < ports; p++) {
            if (me.equals(state.holder(p))) port = p;
        }
        engine.setHolder(port, port >= 0 ? state.start(port) : 0);
    }

    /**
     * A player runs on the present frame (D12), at most the rollback window past the other player's inputs (D84); in
     * lockstep (D189) only frames whose inputs are all in, its own sent {@link #delay} frames ahead.
     */
    private int stepPlayer(RollbackEngine engine, long now) throws IOException {
        if (!clock.known()) return 0;
        int port = engine.myPort();
        if (lockstep) {
            updateDelay(engine);
            Segment ahead = engine.collectLocal(inputs.get(port), delay);
            if (ahead != null) sendInputs(ahead);
        }
        int due = (int) Math.floor(clock.present(now)) - engine.frame();
        if (due <= 0) {
            stalledSince = 0;
            waitingFor = null;
            return 0;
        }
        int ran = 0;
        int confirmedAhead = engine.confirmed() + 1 - engine.frame();
        if (due > FAST_FORWARD_FRAMES && confirmedAhead > 0) {
            int n = Math.min(due - MAX_CATCH_UP, confirmedAhead);
            engine.fastForward(n);
            due -= n;
            ran += n;
        }
        int runnable = runnableFrames(engine, true);
        int n = Math.min(Math.min(due, MAX_CATCH_UP), runnable);
        updateDelay(engine);
        for (int i = 0; i < n; i++) {
            Segment mine = engine.collectLocal(inputs.get(port) | takeMouseMotion(port), delay);
            if (mine != null) sendInputs(mine);
            engine.repair();
            engine.runLive();
        }
        ran += n;
        noteStall(engine, n == 0 && runnable <= 0, now, true);
        return ran;
    }

    /** A spectator stays a few frames behind the confirmed inputs, speeding up or easing off to keep that (D87). */
    private int stepSpectator(RollbackEngine engine, long now) throws IOException {
        int available = engine.confirmed() + 1 - engine.frame();
        if (available > 120) {
            engine.fastForward(available - SPECTATOR_BUFFER);
            spectatorCredit = 0;
            lastStep = now;
            return available - SPECTATOR_BUFFER;
        }
        double speed = 1 + Math.clamp((available - SPECTATOR_BUFFER) * 0.03, -0.15, 0.5);
        spectatorCredit = Math.min(spectatorCredit + (now - lastStep) * fps / 1e9 * speed, MAX_CATCH_UP);
        lastStep = now;
        int n = Math.min((int) spectatorCredit, Math.max(0, available));
        if (n <= 0) {
            if (available <= 0) spectatorCredit = Math.min(spectatorCredit, 1);
            noteStall(engine, available <= 0 && !sessionPaused, now, false);
            return 0;
        }
        spectatorCredit -= n;
        for (int i = 0; i < n; i++) engine.runLive();
        noteStall(engine, false, now, false);
        return n;
    }

    /**
     * Frames a player may run now: no further than the rollback window past the other player's inputs (D83, D84); in
     * lockstep, none past the frames whose inputs are all in (D189).
     */
    private int runnableFrames(RollbackEngine engine, boolean player) {
        if (lockstep) return engine.confirmed() + 1 - engine.frame();
        int limit = engine.confirmed() + MAX_UNCONFIRMED;
        int window = window();
        for (int p = 0; p < ports; p++) {
            UUID holder = state != null ? state.holder(p) : null;
            if (holder != null && (!player || !holder.equals(me))) limit = Math.min(limit, engine.known(p) + window);
        }
        return limit - engine.frame() + 1;
    }

    /** After a second of waiting on someone's inputs, the TV names them (D84). */
    private void noteStall(RollbackEngine engine, boolean stalled, long now, boolean player) {
        if (!stalled || state == null || state.status() != SessionTimeline.RUNNING) {
            stalledSince = 0;
            waitingFor = null;
            return;
        }
        if (stalledSince == 0) stalledSince = now;
        if (now - stalledSince < WAITING_AFTER) return;
        UUID slowest = null;
        int least = Integer.MAX_VALUE;
        for (int p = 0; p < ports; p++) {
            UUID holder = state.holder(p);
            if (holder == null || (player && holder.equals(me))) continue;
            if (engine.known(p) < least) {
                least = engine.known(p);
                slowest = holder;
            }
        }
        waitingFor = slowest;
    }

    /**
     * D83: no delay against nobody; otherwise a frame per 3 frames of one-way latency between the two players, up to
     * the server's maximum. It moves a step at a time, and only after two readings agree, so it doesn't flap.
     * <p>
     * Lockstep (D189): long enough for this player's inputs to come back from the server, and for the slowest other
     * player's to arrive, before their frame; a rise counts at once (the game would stall), a fall waits for two readings.
     */
    private void updateDelay(RollbackEngine engine) {
        if (lockstep && delayFrame < 0) delayFrame = engine.frame() - 60; // the first reading at once
        boolean reading = engine.frame() % 60 == 0 || lockstep && engine.frame() - delayFrame >= 60;
        if (!reading || engine.frame() == delayFrame) return;
        delayFrame = engine.frame();
        double frameMs = 1000.0 / fps, myRtt = rttNanos / 1e6;
        int target = 0;
        if (lockstep) {
            double ms = myRtt;
            for (int p = 0; p < ports; p++) {
                UUID holder = state != null ? state.holder(p) : null;
                if (holder != null && !holder.equals(me)) ms = Math.max(ms, (myRtt + state.rtt(p)) / 2);
            }
            target = Math.clamp((int) Math.ceil(ms / frameMs) + 1, 1, LOCKSTEP_MAX_DELAY);
            if (target > delay) {
                delay = target;
                delayVotes = 0;
                return;
            }
        } else {
            int other = -1;
            for (int p = 0; p < ports; p++) {
                UUID holder = state != null ? state.holder(p) : null;
                if (holder != null && !holder.equals(me)) other = p;
            }
            if (other >= 0) {
                double oneWayMs = (myRtt + state.rtt(other)) / 2;
                target = Math.clamp(Math.round(oneWayMs / frameMs / 3), 0, maxDelay());
            }
        }
        if (target == delay) {
            delayVotes = 0;
        } else if (++delayVotes >= 2) {
            delay += Integer.signum(target - delay);
            delayVotes = 0;
        }
    }

    private static int maxDelay() {
        return CartridgeConfig.synced().maxInputDelayFrames; // the connected server's (D123)
    }

    private static int window() {
        return CartridgeConfig.synced().rollbackFrames; // the connected server's (D123)
    }

    private void sendInputs(Segment segment) {
        int[] buttons = segment.buttons();
        for (int off = 0; off < buttons.length; off += InputFramesPayload.MAX_FRAMES) {
            int n = Math.min(InputFramesPayload.MAX_FRAMES, buttons.length - off);
            send(new InputFramesPayload(game, bootId, segment.port(), segment.start() + off, Arrays.copyOfRange(buttons, off, off + n)));
        }
    }

    private void uploadState(RollbackEngine engine, int requestId) throws IOException {
        RollbackEngine.Snapshot snapshot = engine.finalState();
        byte[] data = kind.deflatesStates() ? States.deflate(snapshot.state()) : snapshot.state();
        byte[] thumbnail = thumbnail(); // in case it goes onto a Memory Card (D98)
        for (int off = 0; off < data.length; off += StateUploadPayload.MAX_CHUNK) {
            int n = Math.min(StateUploadPayload.MAX_CHUNK, data.length - off);
            send(new StateUploadPayload(game, bootId, requestId, snapshot.frame(), data.length, off,
                    Arrays.copyOfRange(data, off, off + n), off == 0 ? thumbnail : new byte[0]));
        }
    }

    private static void send(CustomPacketPayload payload) {
        try {
            if (Minecraft.getInstance().getConnection() != null) Services.NETWORK.sendToServer(payload);
        } catch (RuntimeException e) {
            Cartridge.LOGGER.debug("Couldn't send {}", payload.type().id(), e);
        }
    }

    /** A savestate from the server: deflated where the console's are (D189), for the host as it is. */
    private byte[] fromNetwork(byte[] state) throws IOException {
        return kind.deflatesStates() ? States.inflate(state) : state;
    }

    /**
     * For a console that runs by speed (D196): whether this PC re-runs the server's rollback window, with a savestate
     * kept for each frame as rollback does, in well under a frame's time. Measured from the power-on state on a few
     * tries, the best counting; the power-on state is back afterwards.
     */
    private boolean rollsBackInTime(HostProcess host, RollbackEngine.Emulator emu, byte[] bootState) throws IOException {
        int window = Math.max(1, window());
        int[] none = new int[ports];
        host.run(30, RollbackEngine.MUTE | RollbackEngine.NO_VIDEO, ports, new int[30 * ports]); // past the first frames
        byte[] state = emu.serialize();
        long best = Long.MAX_VALUE;
        for (int attempt = 0; attempt < 4; attempt++) {
            long start = System.nanoTime();
            emu.unserialize(state);
            for (int i = 0; i < window; i++) {
                emu.serialize();
                host.run(1, RollbackEngine.MUTE | RollbackEngine.NO_VIDEO, ports, none);
            }
            best = Math.min(best, System.nanoTime() - start);
        }
        emu.unserialize(bootState);
        double frameNanos = 1e9 / fps;
        boolean fast = best <= ROLLBACK_SHARE * frameNanos;
        Cartridge.LOGGER.info("{}: rolling back {} frames takes {} ms of a {} ms frame: {}", game.describe(), window,
                String.format("%.1f", best / 1e6), String.format("%.1f", frameNanos / 1e6), fast ? "rollback" : "lockstep");
        return fast;
    }

    private RollbackEngine.Emulator emulator(HostProcess host) {
        return new RollbackEngine.Emulator() {
            @Override
            public void run(int frames, int flags, int[] in) throws IOException {
                kind.mapInputs(in, frames); // a fresh array from the engine
                host.run(frames, flags, ports, in);
            }

            @Override
            public byte[] serialize() throws IOException {
                return host.serialize();
            }

            @Override
            public void unserialize(byte[] state) throws IOException {
                host.unserialize(state);
            }

            @Override
            public void reset() throws IOException {
                host.reset();
            }

            @Override
            public long checksum() throws IOException {
                return host.checksum();
            }

            @Override
            public byte[] sram() throws IOException {
                return saves(host);
            }

            @Override
            public void setDevices(int word) throws IOException {
                for (int port = 0; port < ports; port++) {
                    host.setPortDevice(port, switch (SessionTimeline.device(word, port)) {
                        case SessionTimeline.DEVICE_GUN -> HostProcess.DEVICE_LIGHT_GUN;
                        case SessionTimeline.DEVICE_MOUSE -> HostProcess.DEVICE_MOUSE;
                        default -> HostProcess.DEVICE_PAD;
                    });
                }
            }
        };
    }

    /** About 0.4 s of a harsh, fading buzz: what pulling a cart mid-game sounds like (D30). */
    private void buzz() {
        int rate = 48000, chunk = 800;
        short[] buffer = new short[chunk * 2];
        long seed = 0x5DEECE66DL;
        for (int c = 0; c < 24; c++) {
            for (int i = 0; i < chunk; i++) {
                int t = c * chunk + i;
                double fade = 1.0 - t / (24.0 * chunk);
                seed = seed * 6364136223846793005L + 1442695040888963407L;
                double square = ((t / 100) & 1) == 0 ? 1 : -1;
                double noise = ((seed >>> 40) & 0xFFFF) / 32768.0 - 1;
                short s = (short) ((square * 0.6 + noise * 0.4) * fade * 9000);
                buffer[i * 2] = s;
                buffer[i * 2 + 1] = s;
            }
            audio.push(buffer, chunk, rate);
            LockSupport.parkNanos(chunk * 1_000_000_000L / rate);
        }
    }

    private void fail(Component message) {
        error = message;
        status = Status.ERROR;
    }
}
