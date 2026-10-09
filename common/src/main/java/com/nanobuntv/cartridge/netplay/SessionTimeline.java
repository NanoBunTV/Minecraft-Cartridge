package com.nanobuntv.cartridge.netplay;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * The server's record of one console's game (D12, D85, D88): every port's inputs since the latest savestate, the
 * events stamped onto frames (RESET, disk swaps), and the session clock. Plain Java; {@code NetSession} wraps it.
 * <ul>
 *   <li>A held port's inputs come from its holder. Ports nobody holds are filled with zeros up to the held ports, so
 *   a frame is final ({@link #confirmed()}) once every holder has sent it.</li>
 *   <li>From power-on until the first player takes a pad ({@link #ATTRACT}) the clock alone moves the game; after
 *   that, with no pad held, it's {@link #PAUSED} (D85, D88).</li>
 *   <li>Taking a port, and every event, lands a few frames ahead of everything sent so far ({@code lead}), so
 *   clients hear of it before they reach it and never have to correct their guesses.</li>
 * </ul>
 */
public final class SessionTimeline {
    public static final int ATTRACT = 0, RUNNING = 1, PAUSED = 2;
    /**
     * Events on a frame. A load replaces the state before the frame with a Memory Card's savestate (D92). The device
     * events say what the ports hold from that frame on: GUN and PAD port 2 only (D99), DEVICES both ports, its word
     * ({@link #devicesEvent}) in bits 8-15 (D144).
     */
    public static final int EVENT_RESET = 1, EVENT_DISK_SWAP = 2, EVENT_LOAD = 4, EVENT_GUN = 8, EVENT_PAD = 16,
            EVENT_DEVICES = 32;
    public static final int DEVICE_EVENTS = EVENT_GUN | EVENT_PAD | EVENT_DEVICES;
    /** A device event's whole flags, its word included. */
    public static final int DEVICE_BITS = DEVICE_EVENTS | 0xFF00;
    /** What a port holds, in a device word: 2 bits per port, port 1 lowest. */
    public static final int DEVICE_PAD = 0, DEVICE_GUN = 1, DEVICE_MOUSE = 2;

    /** The event for "the ports hold {@code word} from this frame on". */
    public static int devicesEvent(int word) {
        return EVENT_DEVICES | (word & 0xFF) << 8;
    }

    /** The device word a device event sets. */
    public static int devices(int flags) {
        if ((flags & EVENT_DEVICES) != 0) return flags >> 8 & 0xFF;
        return (flags & EVENT_GUN) != 0 ? DEVICE_GUN << 2 : 0;
    }

    /** What {@code port} holds in a device word. */
    public static int device(int word, int port) {
        return word >> 2 * port & 3;
    }

    /** Two events on one frame: flags add up, but a later device event replaces an earlier one. */
    private static int merge(int was, int event) {
        return (event & DEVICE_EVENTS) != 0 ? (was & ~DEVICE_BITS) | event : was | event;
    }
    /** In attract mode the clock fills this many frames ahead, so spectators never run dry between server ticks. */
    static final int ATTRACT_LEAD = 6;

    private final InputLog log;
    private final TreeMap<Integer, Integer> events = new TreeMap<>();
    private final boolean[] held;
    private final int[] start;
    private final double fps;
    private boolean everHeld;
    /** Whether anyone is emulating the game; with nobody there it stands still. */
    private boolean watched;
    private boolean running;
    private double anchorFrame;
    private long anchorNanos;

    public SessionTimeline(int baseFrame, boolean everHeld, double fps, int ports) {
        this.log = new InputLog(baseFrame, ports);
        this.held = new boolean[ports];
        this.start = new int[ports];
        this.everHeld = everHeld;
        this.fps = fps;
        this.anchorFrame = baseFrame;
    }

    public double fps() {
        return fps;
    }

    public int ports() {
        return held.length;
    }

    public int status() {
        for (boolean h : held) {
            if (h) return RUNNING;
        }
        return everHeld ? PAUSED : ATTRACT;
    }

    public boolean everHeld() {
        return everHeld;
    }

    public boolean held(int port) {
        return held[port];
    }

    /** The first frame whose input counts from this port's holder. */
    public int start(int port) {
        return start[port];
    }

    public int base() {
        return log.base();
    }

    public int confirmed() {
        return log.confirmed();
    }

    public int next(int port) {
        return log.next(port);
    }

    /** The frame the game should be at now; it stands still while paused or unwatched. */
    public double present(long now) {
        return running ? anchorFrame + (now - anchorNanos) * fps / 1e9 : anchorFrame;
    }

    /** Someone started or stopped emulating the game (the first in, or the last out). */
    public void setWatched(boolean watched, long now) {
        this.watched = watched;
        updateClock(now);
    }

    /** The server stood still (a paused singleplayer game): don't count that time. */
    public void skipTime(long nanos) {
        anchorNanos += nanos;
    }

    private void updateClock(long now) {
        boolean run = watched && status() != PAUSED;
        if (run == running) return;
        if (run) {
            anchorFrame = Math.max(anchorFrame, confirmed() + 1);
            anchorNanos = now;
        } else {
            anchorFrame = present(now);
        }
        running = run;
    }

    /** A holder's inputs; returns the part that was new, or null if none was (or it doesn't continue the log). */
    public Segment accept(int port, int first, int[] buttons) {
        if (!held[port]) return null;
        int next = log.next(port);
        int end = first + buttons.length;
        if (first > next || end <= next) return null;
        int skip = next - first;
        log.append(port, next, buttons, skip, buttons.length - skip);
        int[] added = new int[buttons.length - skip];
        System.arraycopy(buttons, skip, added, 0, added.length);
        return new Segment(port, next, added);
    }

    /** Fills ports nobody holds with zeros up to the held ports. */
    public List<Segment> fillUnheld() {
        List<Segment> out = new ArrayList<>();
        int target = Integer.MIN_VALUE;
        for (int p = 0; p < held.length; p++) {
            if (held[p]) target = Math.max(target, log.next(p));
        }
        if (target == Integer.MIN_VALUE) return out;
        for (int p = 0; p < held.length; p++) {
            if (!held[p]) fill(p, target, out);
        }
        return out;
    }

    /** Attract mode: the clock moves the game. Call every server tick. */
    public List<Segment> fillByClock(long now) {
        List<Segment> out = new ArrayList<>();
        if (status() != ATTRACT || !running) return out;
        int target = (int) Math.floor(present(now)) + ATTRACT_LEAD + 1;
        for (int p = 0; p < held.length; p++) fill(p, target, out);
        return out;
    }

    private void fill(int port, int end, List<Segment> out) {
        int from = log.next(port);
        if (end <= from) return;
        log.appendZeros(port, end - from);
        out.add(new Segment(port, from, new int[end - from]));
    }

    /**
     * A player takes a port: their inputs count from a frame {@code lead} frames ahead of the present (and past
     * everything sent so far). Returns the zeros filled in before it; {@link #start(int)} tells the frame.
     */
    public List<Segment> take(int port, long now, int lead) {
        everHeld = true;
        held[port] = true;
        updateClock(now); // a paused game starts again
        int first = Math.max(log.maxNext(), (int) Math.ceil(present(now)) + lead);
        start[port] = first;
        List<Segment> out = new ArrayList<>();
        fill(port, first, out);
        out.addAll(fillUnheld());
        return out;
    }

    /** The holder let go; with nobody left holding a pad, the game pauses (D85). */
    public List<Segment> release(int port, long now) {
        held[port] = false;
        updateClock(now);
        return fillUnheld();
    }

    /**
     * Stamps an event onto a frame ahead of everything sent so far. A RESET while paused counts as a fresh start:
     * the game runs again until someone takes a pad (D88).
     */
    public int schedule(int event, long now, int lead) {
        if (event == EVENT_RESET && status() == PAUSED) {
            everHeld = false;
            updateClock(now);
        }
        int frame = Math.max(log.maxNext(), (int) Math.ceil(present(now)) + lead);
        events.merge(frame, event, SessionTimeline::merge);
        return frame;
    }

    /**
     * While paused, runs the free ports on to {@code end} with zeros: a Memory Card load lands on a frame nobody
     * would otherwise reach, and everyone should see the loaded picture (frozen) at once.
     */
    public List<Segment> fillPausedTo(int end) {
        List<Segment> out = new ArrayList<>();
        if (status() != PAUSED) return out;
        for (int p = 0; p < held.length; p++) fill(p, end, out);
        return out;
    }

    /** A holder that falls behind holds the clock back: the present never runs more than {@code window} past the inputs. */
    public void clamp(long now, int window) {
        if (!running || status() != RUNNING) return;
        double limit = confirmed() + 1 + window;
        if (present(now) > limit) {
            anchorFrame = limit;
            anchorNanos = now;
        }
    }

    /** Puts an event on a frame directly: what port 2 holds from the very start of the record. */
    public void mark(int frame, int event) {
        events.merge(frame, event, SessionTimeline::merge);
    }

    /**
     * A new savestate at {@code frame}: forget what came before it. What port 2 holds then carries over as an event
     * on {@code frame}, since savestates don't record it.
     */
    public void rebase(int frame) {
        int device = 0;
        for (int flags : events.headMap(frame).values()) {
            if ((flags & DEVICE_EVENTS) != 0) device = flags & DEVICE_BITS;
        }
        log.dropBefore(frame);
        events.headMap(frame).clear();
        if (device != 0 && (events.getOrDefault(frame, 0) & DEVICE_EVENTS) == 0) mark(frame, device);
    }

    /** Every input from {@code from} on, per port (for a client catching up). */
    public List<Segment> segmentsFrom(int from) {
        List<Segment> out = new ArrayList<>();
        for (int p = 0; p < held.length; p++) {
            int first = Math.max(from, log.base());
            int[] buttons = log.copy(p, first);
            if (buttons.length > 0) out.add(new Segment(p, first, buttons));
        }
        return out;
    }

    public Map<Integer, Integer> eventsFrom(int from) {
        return events.tailMap(from, true);
    }
}
