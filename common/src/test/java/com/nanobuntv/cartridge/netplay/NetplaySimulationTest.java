package com.nanobuntv.cartridge.netplay;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;

/**
 * Two players and spectators on one game through the server's {@link SessionTimeline}, with network latency, a RESET,
 * a disk swap, a Memory Card load, a late joiner and a savestate hand-off. Every client's checksums must match a
 * straight run of the inputs the server ended up with: the rollbacks fixed every wrong guess.
 */
class NetplaySimulationTest {
    private static final long FRAME = 16_666_667L;
    private static final int WINDOW = 12;

    /** A deterministic stand-in for a core: its "RAM" is one number every input changes. */
    static class FakeEmulator implements RollbackEngine.Emulator {
        long s = 1;
        int runs;
        boolean gun;

        @Override
        public void run(int frames, int flags, int[] in) {
            runs++;
            for (int i = 0; i < frames; i++) {
                s = s * 6364136223846793005L + (in[i * 2] & 0xFFFF) * 31L + (in[i * 2 + 1] & 0xFFFF) * (gun ? 4099L : 977L) + 1;
                s ^= s >>> 29;
            }
        }

        @Override
        public byte[] serialize() {
            return ByteBuffer.allocate(8).putLong(s).array();
        }

        @Override
        public void unserialize(byte[] state) {
            s = ByteBuffer.wrap(state).getLong();
        }

        @Override
        public void reset() {
            s = s * 31 + 0x5DEECE66DL;
        }

        @Override
        public long checksum() {
            return s;
        }

        @Override
        public void setDevices(int word) {
            this.gun = SessionTimeline.device(word, 1) == SessionTimeline.DEVICE_GUN;
        }

        @Override
        public byte[] sram() {
            return ByteBuffer.allocate(8).putLong(s >>> 8).array();
        }
    }

    private record Delivery(long tick, Consumer<Client> action) {}

    private static final class Client {
        final String name;
        final int latency; // ticks each way
        final FakeEmulator emu = new FakeEmulator();
        RollbackEngine engine;
        int port = -1;
        final boolean[] remoteHeld = new boolean[2];
        final List<Delivery> inbox = new ArrayList<>();
        final Map<Integer, Long> checksums = new TreeMap<>();

        Client(String name, int latency) {
            this.name = name;
            this.latency = latency;
            this.engine = new RollbackEngine(emu, 0, 2);
        }
    }

    private final SessionTimeline timeline = new SessionTimeline(0, false, 60, 2);
    private final List<Client> clients = new ArrayList<>();
    private final List<Delivery> toServer = new ArrayList<>();
    /** Memory Card savestates the "server" handed out, by frame (NetSession keeps these for joiners). */
    private final Map<Integer, byte[]> loads = new TreeMap<>();
    private long tick;

    private void broadcast(List<Segment> segments, Map<Integer, Integer> events) {
        for (Client c : clients) {
            List<Segment> segs = List.copyOf(segments);
            Map<Integer, Integer> evs = Map.copyOf(events);
            c.inbox.add(new Delivery(tick + c.latency, cl -> {
                for (Segment s : segs) assertTrue(cl.engine.addInputs(s.port(), s.start(), s.buttons()), cl.name + " got a gap");
                evs.forEach(cl.engine::addEvent);
            }));
        }
    }

    /** A Memory Card load: the savestate first, then the event, as NetSession sends them. */
    private void load(byte[] state) {
        int frame = timeline.schedule(SessionTimeline.EVENT_LOAD, tick * FRAME, 8);
        loads.put(frame, state);
        broadcast(timeline.fillPausedTo(frame + 1), Map.of());
        for (Client c : clients) {
            c.inbox.add(new Delivery(tick + c.latency, cl -> {
                cl.engine.addLoad(frame, state);
                cl.engine.addEvent(frame, SessionTimeline.EVENT_LOAD);
            }));
        }
    }

    /** What a joiner from {@code base} is sent: inputs, Memory Card savestates, events. */
    private void catchUp(RollbackEngine engine, int base) {
        for (Segment s : timeline.segmentsFrom(base)) engine.addInputs(s.port(), s.start(), s.buttons());
        loads.forEach((frame, state) -> engine.addLoad(frame, state));
        // What port 2 holds at the base, as the server's rebase carries it over (savestates don't record it).
        int device = 0;
        for (Map.Entry<Integer, Integer> e : timeline.eventsFrom(0).entrySet()) {
            if (e.getKey() < base && (e.getValue() & SessionTimeline.DEVICE_EVENTS) != 0) device = e.getValue() & SessionTimeline.DEVICE_BITS;
        }
        if (device != 0) engine.addEvent(base, device);
        timeline.eventsFrom(base).forEach(engine::addEvent);
    }

    private void take(Client who, int port) {
        int lead = (int) Math.ceil(clients.stream().mapToInt(c -> c.latency).max().orElse(0)) + 2;
        List<Segment> fills = timeline.take(port, tick * FRAME, lead);
        int start = timeline.start(port);
        broadcast(fills, Map.of());
        for (Client c : clients) {
            c.inbox.add(new Delivery(tick + c.latency, cl -> {
                cl.remoteHeld[port] = cl != who;
                if (cl == who) {
                    cl.port = port;
                    cl.engine.setHolder(port, start);
                }
            }));
        }
    }

    private static int buttons(long frame, int port) {
        return (int) (((frame / (7 + port * 4)) * 2654435761L + port) & 0xFF);
    }

    private void step() {
        long now = tick * FRAME;
        // Server: inputs that arrived, then the clock.
        for (Iterator<Delivery> it = toServer.iterator(); it.hasNext(); ) {
            Delivery d = it.next();
            if (d.tick() <= tick) {
                d.action().accept(null);
                it.remove();
            }
        }
        broadcast(timeline.fillByClock(now), Map.of());
        timeline.clamp(now, WINDOW);

        for (Client c : clients) {
            for (Iterator<Delivery> it = c.inbox.iterator(); it.hasNext(); ) {
                Delivery d = it.next();
                if (d.tick() <= tick) {
                    d.action().accept(c);
                    it.remove();
                }
            }
            try {
                c.engine.repair();
                if (c.port >= 0) {
                    int due = (int) Math.floor(timeline.present(now)) - c.engine.frame();
                    for (int i = 0; i < Math.min(due, 6); i++) {
                        int limit = c.engine.confirmed() + 120;
                        for (int p = 0; p < 2; p++) {
                            if (c.remoteHeld[p]) limit = Math.min(limit, c.engine.known(p) + WINDOW);
                        }
                        if (c.engine.frame() > limit) break;
                        Segment mine = c.engine.collectLocal(buttons(c.engine.frame(), c.port), 1);
                        if (mine != null) {
                            Client from = c;
                            toServer.add(new Delivery(tick + c.latency, ignored -> {
                                Segment accepted = timeline.accept(mine.port(), mine.start(), mine.buttons());
                                if (accepted == null) return;
                                List<Segment> out = new ArrayList<>(List.of(accepted));
                                out.addAll(timeline.fillUnheld());
                                broadcast(out, Map.of());
                            }));
                        }
                        c.engine.repair();
                        c.engine.runLive();
                    }
                } else {
                    int available = c.engine.confirmed() + 1 - c.engine.frame();
                    if (available > 120) c.engine.fastForward(available - 4);
                    else for (int i = 0; i < Math.min(2, available - 3); i++) c.engine.runLive();
                }
            } catch (Exception e) {
                throw new AssertionError(c.name + " at tick " + tick, e);
            }
            for (long[] check : c.engine.takeFinalChecksums()) c.checksums.put((int) check[0], check[1]);
        }
        tick++;
    }

    @Test
    void everyClientMatchesAStraightRun() throws Exception {
        Client a = new Client("A", 2), b = new Client("B", 6), spectator = new Client("S", 4);
        clients.addAll(List.of(a, b, spectator));
        timeline.setWatched(true, 0);
        while (tick < 30) step();       // attract mode: the clock runs the game
        take(a, 0);
        while (tick < 70) step();       // A plays alone
        take(b, 1);
        while (tick < 300) step();
        broadcast(List.of(), Map.of(timeline.schedule(SessionTimeline.EVENT_RESET, tick * FRAME, 8), SessionTimeline.EVENT_RESET));
        while (tick < 420) step();
        broadcast(List.of(), Map.of(timeline.schedule(SessionTimeline.EVENT_DISK_SWAP, tick * FRAME, 8), SessionTimeline.EVENT_DISK_SWAP));
        while (tick < 440) step();
        // The light gun goes into port 2 (D99): from a frame on, port 2's input means something else.
        broadcast(List.of(), Map.of(timeline.schedule(SessionTimeline.EVENT_GUN, tick * FRAME, 8), SessionTimeline.EVENT_GUN));
        while (tick < 460) step();
        // Someone loads a Memory Card (D92): a state from elsewhere replaces the game's on a frame.
        FakeEmulator elsewhere = new FakeEmulator();
        elsewhere.s = 0x1234_5678_9ABCL;
        load(elsewhere.serialize());
        while (tick < 500) step();

        // A late joiner catches up from power-on; another starts from A's savestate (D12).
        Client late = new Client("late", 3);
        catchUp(late.engine, 0);
        late.remoteHeld[0] = late.remoteHeld[1] = true;
        clients.add(late);

        RollbackEngine.Snapshot snapshot = a.engine.finalState();
        Client handed = new Client("handed", 5);
        handed.emu.unserialize(snapshot.state());
        handed.engine = new RollbackEngine(handed.emu, snapshot.frame(), 2);
        catchUp(handed.engine, snapshot.frame());
        clients.add(handed);

        while (tick < 900) step();

        // The straight run: every input the server holds, no guessing.
        FakeEmulator refEmu = new FakeEmulator();
        RollbackEngine ref = new RollbackEngine(refEmu, 0, 2);
        catchUp(ref, 0);
        Map<Integer, Long> expected = new TreeMap<>();
        while (ref.frame() <= ref.confirmed()) {
            ref.runLive();
            for (long[] check : ref.takeFinalChecksums()) expected.put((int) check[0], check[1]);
        }

        assertTrue(expected.size() >= 12, "checkpoints: " + expected.size());
        for (Client c : clients) {
            assertFalse(c.checksums.isEmpty(), c.name + " reported no checksums");
            for (Map.Entry<Integer, Long> e : c.checksums.entrySet()) {
                assertEquals(expected.get(e.getKey()), e.getValue(), c.name + " at frame " + e.getKey());
            }
        }
        assertTrue(a.engine.rollbacks() > 0 && b.engine.rollbacks() > 0, "the players had to correct guesses");

        // The load really replaced the game: with other inputs before it, everything after it is the same.
        int loadedAt = loads.keySet().iterator().next();
        RollbackEngine other = new RollbackEngine(new FakeEmulator(), 0, 2);
        for (Segment s : timeline.segmentsFrom(0)) {
            int[] buttons = s.buttons().clone();
            for (int f = s.start(); f < Math.min(loadedAt, s.end()); f++) buttons[f - s.start()] ^= 0x55;
            other.addInputs(s.port(), s.start(), buttons);
        }
        loads.forEach(other::addLoad);
        timeline.eventsFrom(0).forEach(other::addEvent);
        int compared = 0;
        while (other.frame() <= other.confirmed()) {
            other.runLive();
            for (long[] check : other.takeFinalChecksums()) {
                if (check[0] > loadedAt) {
                    assertEquals(expected.get((int) check[0]), check[1], "after the load, frame " + check[0]);
                    compared++;
                } else if (check[0] > 60) {
                    assertTrue(expected.get((int) check[0]) != check[1], "before the load the inputs differ");
                }
            }
        }
        assertTrue(compared >= 5);
        assertEquals(0, spectator.engine.rollbacks(), "spectators never guess (D87)");
    }

    @Test
    void pausesWhenTheLastPlayerLetsGo() {
        Client a = new Client("A", 2);
        clients.add(a);
        timeline.setWatched(true, 0);
        take(a, 0);
        while (tick < 100) step();
        assertEquals(SessionTimeline.RUNNING, timeline.status());
        timeline.release(0, tick * FRAME);
        assertEquals(SessionTimeline.PAUSED, timeline.status());
        int confirmed = timeline.confirmed();
        double present = timeline.present(tick * FRAME);
        tick += 600;
        assertEquals(present, timeline.present(tick * FRAME), "the clock stands still while paused (D85)");
        assertTrue(timeline.fillByClock(tick * FRAME).isEmpty());
        assertEquals(confirmed, timeline.confirmed());

        // A Memory Card loaded while paused: the free ports run on to it, so everyone sees it at once, still paused.
        int at = timeline.schedule(SessionTimeline.EVENT_LOAD, tick * FRAME, 2);
        timeline.fillPausedTo(at + 1);
        assertEquals(at, timeline.confirmed());
        assertEquals(SessionTimeline.PAUSED, timeline.status());
        assertEquals(present, timeline.present(tick * FRAME));

        // A RESET while paused starts it running again until someone takes a pad (D88).
        timeline.schedule(SessionTimeline.EVENT_RESET, tick * FRAME, 2);
        assertEquals(SessionTimeline.ATTRACT, timeline.status());
        assertFalse(timeline.fillByClock((tick + 10) * FRAME).isEmpty());
    }

    @Test
    void fourPortsReachTheEmulator() throws Exception {
        // The Pixelon 64 (D189): four ports, and inputs past 16 bits (its stick).
        int[][] seen = new int[1][];
        RollbackEngine.Emulator emu = new FakeEmulator() {
            @Override
            public void run(int frames, int flags, int[] in) {
                seen[0] = in.clone();
            }
        };
        RollbackEngine engine = new RollbackEngine(emu, 0, 4);
        assertEquals(4, engine.ports());
        int stick = com.nanobuntv.cartridge.emu.N64Pad.encode(1, -1F, 0.5F);
        for (int p = 0; p < 4; p++) engine.addInputs(p, 0, new int[] {p == 3 ? stick : p + 1});
        assertEquals(0, engine.confirmed());
        engine.runLive();
        assertTrue(java.util.Arrays.equals(new int[] {1, 2, 3, stick}, seen[0]), java.util.Arrays.toString(seen[0]));

        SessionTimeline four = new SessionTimeline(0, false, 60, 4);
        four.setWatched(true, 0);
        List<Segment> filled = four.take(2, 0, 3);
        assertEquals(4, four.ports());
        assertTrue(filled.stream().anyMatch(s -> s.port() == 3), "the free ports fill up to the held one");
        assertEquals(3, four.start(2));
        assertTrue(four.accept(2, 3, new int[] {stick, stick}) != null);
        four.fillUnheld(); // as NetSession does after every accepted run
        assertEquals(4, four.confirmed(), "free ports follow the holder");
    }

    @Test
    void batteryRamIsSampledFromFinalFramesOnly() throws Exception {
        FakeEmulator emu = new FakeEmulator();
        RollbackEngine engine = new RollbackEngine(emu, 0, 2);
        engine.trackSram(true);
        engine.addInputs(0, 0, new int[150]);
        engine.addInputs(1, 0, new int[150]);
        for (int i = 0; i < 200; i++) engine.runLive(); // 50 frames past the inputs: guesses
        RollbackEngine.Snapshot sample = engine.takeFinalSram();
        assertEquals(120, sample.frame(), "the newest checkpoint whose frames are all final");
        assertEquals(null, engine.takeFinalSram(), "each sample is taken once");

        // The sample is the battery RAM before that frame, as a straight run has it.
        FakeEmulator straight = new FakeEmulator();
        straight.run(120, 0, new int[240]);
        assertTrue(java.util.Arrays.equals(straight.sram(), sample.state()));

        engine.addInputs(0, 150, new int[60]);
        engine.addInputs(1, 150, new int[60]);
        engine.runLive();
        assertEquals(180, engine.takeFinalSram().frame());
    }

    @Test
    void whatPortTwoHoldsSurvivesARebase() {
        timeline.setWatched(true, 0);
        timeline.fillByClock(100 * FRAME);
        int at = timeline.schedule(SessionTimeline.EVENT_GUN, 10 * FRAME, 2);
        timeline.fillByClock(200 * FRAME);
        timeline.rebase(at + 50);
        assertEquals(SessionTimeline.EVENT_GUN, timeline.eventsFrom(0).get(at + 50), "a joiner from the new base gets the gun too");
    }

    @Test
    void takenPortsStartAheadOfEverythingSent() {
        timeline.setWatched(true, 0);
        timeline.fillByClock(60 * FRAME);
        int before = Math.max(timeline.next(0), timeline.next(1));
        timeline.take(1, 60 * FRAME, 5);
        assertTrue(timeline.start(1) >= before, "a take never rewrites inputs already sent");
        assertEquals(timeline.start(1), timeline.next(1));
        assertEquals(timeline.next(1), timeline.next(0), "the free port is filled up to the held one");
        assertEquals(SessionTimeline.RUNNING, timeline.status());
    }
}
