package com.nanobuntv.cartridge.netplay;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * One client's copy of a networked game (D12): it runs frames with the inputs it knows, guesses the rest (a remote
 * player keeps pressing what they pressed last), and when a real input turns out different it loads the savestate
 * from before that frame and runs forward again. Plain Java over an {@link Emulator}, so it's tested without a core.
 * <p>
 * Frames are counted from the game's start; {@link #frame()} is the next one to run (the emulator's state is "before
 * frame {@code frame}"). A savestate is kept for every frame run on a guess, until the guess is confirmed. Not
 * thread-safe: the console's emulator thread owns it.
 */
public final class RollbackEngine {
    public interface Emulator {
        /** Runs frames; {@code inputs} holds {@link #ports()} inputs per frame ({@link InputLog}). */
        void run(int frames, int flags, int[] inputs) throws IOException;

        byte[] serialize() throws IOException;

        void unserialize(byte[] state) throws IOException;

        void reset() throws IOException;

        long checksum() throws IOException;

        /** The game's battery RAM (D24); empty if it has none. */
        byte[] sram() throws IOException;

        /** What each port holds, a {@link SessionTimeline} device word (D99, D144). */
        void setDevices(int word) throws IOException;
    }

    /** The guesses can't be fixed: the state to go back to is gone. The caller asks the server for a resync. */
    public static final class DesyncException extends IOException {
        public DesyncException(String message) {
            super(message);
        }
    }

    /** Emulator.run flags, as cartridge-host's RUN (emu/HostProcess). */
    public static final int RENDER_LAST_ONLY = 1, MUTE = 2, NO_VIDEO = 4;
    /** Checksums are taken at frames divisible by this (D12). */
    public static final int CHECK_INTERVAL = 60;
    /**
     * A disk swap (D74) as FCEUmm's own disk buttons on pad 1: R opens the drive, L picks the next side, R closes it.
     * Rows are {first frame, last frame, buttons} counted from the event's frame.
     */
    private static final int[][] DISK_SWAP = {{0, 2, 1 << 11}, {45, 47, 1 << 10}, {52, 54, 1 << 11}};
    private static final int DISK_SWAP_FRAMES = 60;
    /** Events that change the state or the ports, so a run of frames stops before them. */
    private static final int STATE_EVENTS = SessionTimeline.EVENT_RESET | SessionTimeline.EVENT_LOAD
            | SessionTimeline.DEVICE_EVENTS;

    private enum Mode { LIVE, RESIMULATE, FAST_FORWARD }

    private final Emulator emu;
    private final InputLog log;
    private final TreeMap<Integer, Integer> events = new TreeMap<>();
    /** State before frame F, for each frame run while it wasn't confirmed yet. */
    private final Map<Integer, byte[]> states = new HashMap<>();
    /** What frame F ran with: each port's input, then the events. Only for frames run on a guess. */
    private final Map<Integer, int[]> used = new HashMap<>();
    /** This player's own inputs, sent but not yet echoed by the server. */
    private final TreeMap<Integer, Integer> local = new TreeMap<>();
    /** Checksums of the state before frame F, waiting until F's inputs are final. */
    private final TreeMap<Integer, Long> checks = new TreeMap<>();
    /** Battery RAM before frame F, likewise, while {@link #trackSram} is on (D95). */
    private final TreeMap<Integer, byte[]> srams = new TreeMap<>();
    /** Memory Card savestates that replace the state before their frame (EVENT_LOAD, D92). */
    private final TreeMap<Integer, byte[]> loads = new TreeMap<>();

    private int frame;
    private int myPort = -1, myStart, nextSend;
    private int repairFrom = Integer.MAX_VALUE;
    private int rollbacks;
    private boolean trackSram;
    /** What the emulator's ports hold now (a device word), or -1 unknown. */
    private int device = -1;

    /** {@code emu} is already in the state before {@code baseFrame}. */
    public RollbackEngine(Emulator emu, int baseFrame, int ports) {
        this.emu = emu;
        this.log = new InputLog(baseFrame, ports);
        this.frame = baseFrame;
    }

    public int frame() {
        return frame;
    }

    public int ports() {
        return log.ports();
    }

    /** The last frame whose inputs are all final. */
    public int confirmed() {
        return log.confirmed();
    }

    /** The last frame of this port the server has confirmed. */
    public int known(int port) {
        return log.next(port) - 1;
    }

    public int rollbacks() {
        return rollbacks;
    }

    public int myPort() {
        return myPort;
    }

    // --- What the server says -----------------------------------------------------------------------------------------

    /** Inputs from the server. Returns false if they don't continue what's known (a gap: resync). */
    public boolean addInputs(int port, int first, int[] buttons) {
        int next = log.next(port);
        int end = first + buttons.length;
        if (end <= next) return true; // already known
        if (first > next) return false;
        int skip = next - first;
        for (int f = next; f < end; f++) {
            int value = buttons[f - first];
            int[] u = f < frame ? used.get(f) : null;
            if (u != null && u[port] != value) mispredicted(f);
        }
        log.append(port, next, buttons, skip, buttons.length - skip);
        if (port == myPort) local.headMap(log.next(port)).clear();
        return true;
    }

    /** The savestate a load event at {@code at} brings in; it arrives before the event does. */
    public void addLoad(int at, byte[] state) {
        if (at >= log.base()) loads.put(at, state);
    }

    /** An event stamped onto a frame (D12): RESET, a disk swap or a Memory Card load. */
    public void addEvent(int at, int flags) {
        if (at < log.base()) return;
        int merged = events.merge(at, flags, (a, b) -> a | b);
        if (at < frame) {
            int[] u = used.get(at);
            if (u == null || u[log.ports()] != merged) mispredicted(at);
        }
    }

    /** This client holds {@code port} from frame {@code start} on, or -1 for none. */
    public void setHolder(int port, int start) {
        if (port == myPort && (port < 0 || start == myStart)) return;
        myPort = port;
        myStart = start;
        nextSend = start;
        local.clear();
    }

    /**
     * Records this player's buttons for every frame up to {@code frame + delay} not yet sent, and returns them to send
     * to the server (null if there's nothing new). Frames already run on another guess are corrected.
     */
    public Segment collectLocal(int buttons, int delay) {
        if (myPort < 0) return null;
        int from = Math.max(Math.max(nextSend, myStart), log.next(myPort));
        int to = frame + delay;
        if (to < from) return null;
        int[] out = new int[to - from + 1];
        for (int f = from; f <= to; f++) {
            out[f - from] = buttons;
            local.put(f, buttons);
            int[] u = f < frame ? used.get(f) : null;
            if (u != null && u[myPort] != buttons) mispredicted(f);
        }
        nextSend = to + 1;
        return new Segment(myPort, from, out);
    }

    private void mispredicted(int at) {
        repairFrom = Math.min(repairFrom, at);
    }

    // --- Running ------------------------------------------------------------------------------------------------------

    /** Fixes any wrong guess found since the last call: back to the state before it, then forward again, silently. */
    public void repair() throws IOException {
        int from = repairFrom;
        repairFrom = Integer.MAX_VALUE;
        if (from >= frame) return;
        byte[] state = states.get(from);
        if (state == null) throw new DesyncException("no savestate for frame " + from + " (at " + frame + ")");
        emu.unserialize(state);
        int target = frame;
        frame = from;
        run(target - from, Mode.RESIMULATE);
        rollbacks++;
    }

    /** Runs one frame for real: picture and sound. */
    public void runLive() throws IOException {
        run(1, Mode.LIVE);
    }

    /** Catches up {@code count} frames as fast as possible, silently, showing only the last. */
    public void fastForward(int count) throws IOException {
        if (count > 0) run(count, Mode.FAST_FORWARD);
    }

    private void run(int count, Mode mode) throws IOException {
        int end = frame + count;
        while (frame < end) {
            boolean last;
            if (frame > confirmed()) {
                // On a guess: keep the state from before this frame, and what it ran with.
                states.put(frame, emu.serialize());
                int ev = events.getOrDefault(frame, 0);
                int[] u = new int[log.ports() + 1];
                for (int p = 0; p < log.ports(); p++) u[p] = input(p, frame);
                u[log.ports()] = ev;
                used.put(frame, u);
                applyEvents(ev);
                last = frame + 1 == end;
                emu.run(1, flags(mode, last, 1), effectiveInputs(frame, 1));
                frame++;
            } else {
                // Final inputs: one RUN up to the next reset or load, checkpoint or guess.
                applyEvents(events.getOrDefault(frame, 0));
                int stop = Math.min(end, confirmed() + 1);
                Integer cut = events.ceilingKey(frame + 1);
                while (cut != null && (events.get(cut) & STATE_EVENTS) == 0) {
                    cut = events.ceilingKey(cut + 1);
                }
                if (cut != null) stop = Math.min(stop, cut);
                if (mode != Mode.FAST_FORWARD) stop = Math.min(stop, (frame / CHECK_INTERVAL + 1) * CHECK_INTERVAL);
                int n = stop - frame;
                last = stop == end;
                emu.run(n, flags(mode, last, n), effectiveInputs(frame, n));
                frame = stop;
            }
            if (mode != Mode.FAST_FORWARD && frame % CHECK_INTERVAL == 0) {
                checks.put(frame, emu.checksum());
                if (trackSram) srams.put(frame, emu.sram());
            }
        }
        prune();
    }

    /**
     * What happens before {@link #frame} runs: a Memory Card's savestate comes in, then RESET; and port 2 holds what
     * the events up to this frame say (a rollback may cross a change, and savestates don't record it).
     */
    private void applyEvents(int ev) throws IOException {
        if ((ev & SessionTimeline.EVENT_LOAD) != 0) {
            byte[] state = loads.get(frame);
            if (state == null) throw new DesyncException("no savestate for the load at frame " + frame);
            emu.unserialize(state);
        }
        if ((ev & SessionTimeline.EVENT_RESET) != 0) emu.reset();
        int want = 0;
        for (int flags : events.headMap(frame, true).descendingMap().values()) {
            if ((flags & SessionTimeline.DEVICE_EVENTS) != 0) {
                want = SessionTimeline.devices(flags);
                break;
            }
        }
        if (want != device) {
            emu.setDevices(want);
            device = want;
        }
    }

    private static int flags(Mode mode, boolean last, int frames) {
        return switch (mode) {
            case LIVE -> frames > 1 ? RENDER_LAST_ONLY : 0;
            case RESIMULATE, FAST_FORWARD -> MUTE | (last ? RENDER_LAST_ONLY : NO_VIDEO);
        };
    }

    /** The input a port uses at a frame: known, this player's own, or the guess (the last known, held). */
    private int input(int port, int f) {
        if (log.has(port, f)) return log.get(port, f);
        if (port == myPort) {
            Integer mine = local.get(f);
            if (mine != null) return mine;
        }
        return log.last(port);
    }

    private int[] effectiveInputs(int first, int n) {
        int ports = log.ports();
        int[] in = new int[n * ports];
        for (int i = 0; i < n; i++) {
            int f = first + i;
            for (int p = 0; p < ports; p++) in[i * ports + p] = input(p, f);
            in[i * ports] |= diskSwapButtons(f);
        }
        return in;
    }

    private int diskSwapButtons(int f) {
        for (Map.Entry<Integer, Integer> e : events.subMap(f - DISK_SWAP_FRAMES + 1, true, f, true).descendingMap().entrySet()) {
            if ((e.getValue() & SessionTimeline.EVENT_DISK_SWAP) == 0) continue;
            int step = f - e.getKey();
            for (int[] row : DISK_SWAP) {
                if (step >= row[0] && step <= row[1]) return row[2];
            }
            return 0;
        }
        return 0;
    }

    private void prune() {
        int c = confirmed();
        states.keySet().removeIf(f -> f <= c);
        used.keySet().removeIf(f -> f <= c);
        loads.headMap(Math.min(frame, c + 1)).clear(); // run, and no rollback reaches back to them
    }

    // --- What goes back to the server --------------------------------------------------------------------------------

    /** Checksums whose frames are final now, as {frame, checksum} pairs; each is returned once. */
    public List<long[]> takeFinalChecksums() {
        List<long[]> out = new ArrayList<>();
        int c = confirmed();
        while (!checks.isEmpty() && checks.firstKey() - 1 <= c) {
            var e = checks.pollFirstEntry();
            if (e.getKey() <= frame) out.add(new long[] {e.getKey(), e.getValue()});
        }
        return out;
    }

    /** Samples the battery RAM at every checkpoint from now on (the authority, D95), or stops. */
    public void trackSram(boolean on) {
        trackSram = on;
        if (!on) srams.clear();
    }

    /** The newest battery RAM sample whose frames are all final, or null if no new one is; older ones are dropped. */
    public Snapshot takeFinalSram() {
        Snapshot newest = null;
        int c = confirmed();
        while (!srams.isEmpty() && srams.firstKey() - 1 <= c && srams.firstKey() <= frame) {
            var e = srams.pollFirstEntry();
            newest = new Snapshot(e.getKey(), e.getValue());
        }
        return newest;
    }

    /** The newest state that no late input can change, for a savestate hand-off: {frame, state}. */
    public Snapshot finalState() throws IOException {
        int c = confirmed();
        if (frame <= c + 1) return new Snapshot(frame, emu.serialize());
        byte[] state = states.get(c + 1);
        if (state == null) throw new DesyncException("no savestate for frame " + (c + 1));
        return new Snapshot(c + 1, state);
    }

    public record Snapshot(int frame, byte[] state) {}
}
