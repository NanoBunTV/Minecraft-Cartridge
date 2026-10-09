package com.nanobuntv.cartridge.emu;

import java.io.BufferedReader;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * One cartridge-host process (D7) and its protocol (sidecar/src/protocol.h): commands over the host's stdin,
 * replies over its stdout, video and audio in a {@link SharedMemory} file. Not thread-safe: one owner thread per
 * host. The host exits when this closes or when the game dies (its stdin closes); one that hangs is killed by
 * {@link #kill()}, and every host still running when the game exits is killed then.
 */
public final class HostProcess implements AutoCloseable {
    public static final int PROTOCOL_VERSION = 6;
    /** RUN flag: only the last frame reaches shared memory (rollback re-simulation). */
    public static final int RUN_RENDER_LAST_ONLY = 1;
    /** RUN flag: drop these frames' audio (re-simulated audio was already played). */
    public static final int RUN_MUTE = 2;
    /** RUN flag: none of these frames reaches shared memory (rollback steps before the last). */
    public static final int RUN_NO_VIDEO = 4;
    /**
     * setPortDevice kinds: a pad, the core's light gun (D99), whose input is {@link LightGun#encode}'s aim, or its
     * mouse (D144), whose input is {@link Mouse#encode}'s movement.
     */
    public static final int DEVICE_PAD = 0, DEVICE_LIGHT_GUN = 1, DEVICE_MOUSE = 2;

    private static final int HELLO = 0, LOAD_CORE = 1, SET_OPTION = 2, LOAD_GAME = 3, RUN = 4, SERIALIZE = 5,
            UNSERIALIZE = 6, GET_SRAM = 7, SET_SRAM = 8, RESET = 9, CHECKSUM = 10, SHUTDOWN = 11,
            SET_PORT_DEVICE = 12, LOAD_GAME_LINKED = 13, GET_MEMORY = 14, SET_MEMORY = 15, SET_CHECKSUM = 16;
    /** {@link #setChecksum} kinds: the game's RAM (the default), or the last picture (D194). */
    public static final int CHECKSUM_RAM = 0, CHECKSUM_PICTURE = 1;
    /**
     * SameBoy's linked Game Boys (D177): the subsystem, and each Game Boy's battery RAM as a memory id, SameBoy's
     * RETRO_MEMORY_GAMEBOY_1_SRAM ((1 << 8) | RETRO_MEMORY_SAVE_RAM) and RETRO_MEMORY_GAMEBOY_2_SRAM ((3 << 8) | ...).
     */
    public static final String SUBSYSTEM_GAME_BOY_LINK = "gb_link_2p";
    public static final int MEMORY_SAVE_RAM = 0, MEMORY_GAME_BOY_1_SRAM = 0x100, MEMORY_GAME_BOY_2_SRAM = 0x300;
    private static final Logger LOGGER = LoggerFactory.getLogger("cartridge-host");
    /** Every host process not yet exited, killed by a shutdown hook (a hung host doesn't notice its stdin closing). */
    private static final Set<Process> LIVE = ConcurrentHashMap.newKeySet();

    static {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> LIVE.forEach(Process::destroyForcibly), "cartridge-host reaper"));
    }

    /** What the core reports after loading a game. */
    public record AvInfo(int baseWidth, int baseHeight, int maxWidth, int maxHeight, double fps, double sampleRate,
                         int serializeSize, int region, long serializationQuirks) {}

    /** A command the host answered with an error (the process is still usable). */
    public static final class HostException extends IOException {
        public HostException(String message) {
            super(message);
        }
    }

    private final Process process;
    private final OutputStream toHost;
    private final InputStream fromHost;
    private final Path shmFile;
    private final SharedMemory shm;
    private final String hostVersion;

    private HostProcess(Process process, Path shmFile) throws IOException {
        this.process = process;
        this.toHost = process.getOutputStream();
        this.fromHost = process.getInputStream();
        this.shmFile = shmFile;
        try {
            ByteBuffer hello = ByteBuffer.wrap(call(HELLO, new byte[0])).order(ByteOrder.LITTLE_ENDIAN);
            int version = hello.getInt();
            if (version != PROTOCOL_VERSION) {
                throw new IOException("cartridge-host speaks protocol " + version + ", expected " + PROTOCOL_VERSION);
            }
            this.hostVersion = StandardCharsets.UTF_8.decode(hello).toString();
            this.shm = SharedMemory.open(shmFile);
        } catch (IOException | RuntimeException e) {
            process.destroyForcibly();
            try {
                Files.deleteIfExists(shmFile);
            } catch (IOException ignored) {
                // SharedMemory.cleanStale removes it on a later launch
            }
            throw e;
        }
    }

    /**
     * Starts a host. {@code shmFile} must not be in use by another host; it's created by the host and deleted on
     * {@link #close()}. {@code systemDir} is the core's system and save directory (BIOS files); battery saves don't
     * go there but through {@link #getSram()} (D24).
     */
    public static HostProcess start(Path hostExe, Path shmFile, Path systemDir) throws IOException {
        return start(hostExe, shmFile, systemDir, null);
    }

    /**
     * As {@link #start(Path, Path, Path)}, with every line the host and its core log also handed to {@code log} (on
     * the log thread): what a core says about a game it can't run (MAME names the files a romset lacks, D206).
     */
    public static HostProcess start(Path hostExe, Path shmFile, Path systemDir,
            @org.jetbrains.annotations.Nullable java.util.function.Consumer<String> log) throws IOException {
        Files.createDirectories(shmFile.toAbsolutePath().getParent());
        Files.createDirectories(systemDir);
        Process process = new ProcessBuilder(hostExe.toString(), "--shm", shmFile.toAbsolutePath().toString(),
                "--system-dir", systemDir.toAbsolutePath().toString()).start();
        LIVE.add(process);
        process.onExit().thenRun(() -> LIVE.remove(process));
        Thread logs = new Thread(() -> drainLog(process, log), "cartridge-host log");
        logs.setDaemon(true);
        logs.start();
        return new HostProcess(process, shmFile);
    }

    private static void drainLog(Process process, @org.jetbrains.annotations.Nullable java.util.function.Consumer<String> log) {
        try (BufferedReader err = new BufferedReader(
                new InputStreamReader(process.getErrorStream(), StandardCharsets.UTF_8))) {
            for (String line; (line = err.readLine()) != null; ) {
                if (log != null) log.accept(line);
                // The core's own info and messages are chatty on every power-on; warnings and the host's own lines aren't.
                if (line.startsWith("[core info]") || line.startsWith("[core message]") || line.startsWith("[core debug]")) {
                    LOGGER.debug(line);
                } else {
                    LOGGER.info(line);
                }
            }
        } catch (IOException ignored) {
            // the host is gone
        }
    }

    public String hostVersion() {
        return hostVersion;
    }

    public SharedMemory sharedMemory() {
        return shm;
    }

    public boolean isAlive() {
        return process.isAlive();
    }

    /**
     * Kills the host at once, from any thread: for one that stopped answering (a hung core), so the owner thread
     * blocked in a call gets an {@link EOFException} and can clean up.
     */
    public void kill() {
        process.destroyForcibly();
    }

    /** Loads a core library; returns its system info (name, version, extensions, need_fullpath). */
    public Map<String, String> loadCore(Path library) throws IOException {
        String text = new String(call(LOAD_CORE, library.toAbsolutePath().toString().getBytes(StandardCharsets.UTF_8)),
                StandardCharsets.UTF_8);
        Map<String, String> info = new LinkedHashMap<>();
        for (String line : text.split("\n")) {
            int eq = line.indexOf('=');
            if (eq > 0) info.put(line.substring(0, eq), line.substring(eq + 1));
        }
        return info;
    }

    /** Sets a core option before {@link #loadGame}; netplay pins these for everyone (Reference-Libretro.md). */
    public void setOption(String key, String value) throws IOException {
        call(SET_OPTION, (key + "=" + value).getBytes(StandardCharsets.UTF_8));
    }

    /** {@code name} only needs the right extension (e.g. "game.nes"); the ROM is passed in memory. */
    public AvInfo loadGame(String name, byte[] rom) throws IOException {
        byte[] nameBytes = name.getBytes(StandardCharsets.UTF_8);
        ByteBuffer payload = ByteBuffer.allocate(4 + nameBytes.length + rom.length).order(ByteOrder.LITTLE_ENDIAN);
        payload.putInt(nameBytes.length).put(nameBytes).put(rom);
        ByteBuffer r = ByteBuffer.wrap(call(LOAD_GAME, payload.array())).order(ByteOrder.LITTLE_ENDIAN);
        return new AvInfo(r.getInt(), r.getInt(), r.getInt(), r.getInt(), r.getDouble(), r.getDouble(), r.getInt(),
                r.getInt(), r.getLong());
    }

    /** Two games as one through a core subsystem ({@link #SUBSYSTEM_GAME_BOY_LINK}); otherwise as {@link #loadGame}. */
    public AvInfo loadGameLinked(String subsystem, String[] names, byte[][] roms) throws IOException {
        byte[] ident = subsystem.getBytes(StandardCharsets.UTF_8);
        int size = 4 + ident.length;
        for (int i = 0; i < 2; i++) size += 8 + names[i].getBytes(StandardCharsets.UTF_8).length + roms[i].length;
        ByteBuffer payload = ByteBuffer.allocate(size).order(ByteOrder.LITTLE_ENDIAN);
        payload.putInt(ident.length).put(ident);
        for (int i = 0; i < 2; i++) {
            byte[] name = names[i].getBytes(StandardCharsets.UTF_8);
            payload.putInt(name.length).put(name).putInt(roms[i].length).put(roms[i]);
        }
        ByteBuffer r = ByteBuffer.wrap(call(LOAD_GAME_LINKED, payload.array())).order(ByteOrder.LITTLE_ENDIAN);
        return new AvInfo(r.getInt(), r.getInt(), r.getInt(), r.getInt(), r.getDouble(), r.getDouble(), r.getInt(),
                r.getInt(), r.getLong());
    }

    /**
     * Runs {@code frames} frames. {@code inputs} holds {@code ports} inputs per frame: a joypad bitmask (bit n =
     * RETRO_DEVICE_ID_JOYPAD_n) and a stick in the upper 16 bits ({@link N64Pad}). Returns the number of frames run since
     * the game loaded.
     */
    public long run(int frames, int flags, int ports, int[] inputs) throws IOException {
        if (inputs.length != frames * ports) throw new IllegalArgumentException("need frames * ports inputs");
        ByteBuffer payload = ByteBuffer.allocate(12 + inputs.length * 4).order(ByteOrder.LITTLE_ENDIAN);
        payload.putInt(frames).putInt(flags).putInt(ports);
        for (int i : inputs) payload.putInt(i);
        return ByteBuffer.wrap(call(RUN, payload.array())).order(ByteOrder.LITTLE_ENDIAN).getLong();
    }

    public byte[] serialize() throws IOException {
        return call(SERIALIZE, new byte[0]);
    }

    public void unserialize(byte[] state) throws IOException {
        call(UNSERIALIZE, state);
    }

    /** Battery RAM (empty if the game has none); it travels with the cartridge (D24). */
    public byte[] getSram() throws IOException {
        return call(GET_SRAM, new byte[0]);
    }

    public void setSram(byte[] sram) throws IOException {
        call(SET_SRAM, sram);
    }

    /** A libretro memory region by id ({@link #MEMORY_GAME_BOY_1_SRAM}, ...); empty if absent. */
    public byte[] getMemory(int id) throws IOException {
        return call(GET_MEMORY, ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(id).array());
    }

    public void setMemory(int id, byte[] data) throws IOException {
        call(SET_MEMORY, ByteBuffer.allocate(4 + data.length).order(ByteOrder.LITTLE_ENDIAN).putInt(id).put(data).array());
    }

    /** What's plugged into a port: {@link #DEVICE_PAD}, {@link #DEVICE_LIGHT_GUN} or {@link #DEVICE_MOUSE}. Kept across savestates. */
    public void setPortDevice(int port, int kind) throws IOException {
        call(SET_PORT_DEVICE, ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN).putInt(port).putInt(kind).array());
    }

    /** The console's RESET button (D28). */
    public void reset() throws IOException {
        call(RESET, new byte[0]);
    }

    /**
     * What {@link #checksum} hashes from now on: {@link #CHECKSUM_RAM}, or {@link #CHECKSUM_PICTURE}, every picture the
     * core draws (video off or not), for a core without RAM to show whose savestates differ harmlessly (MAME, D194).
     */
    public void setChecksum(int kind) throws IOException {
        call(SET_CHECKSUM, ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(kind).array());
    }

    /** Hash of the game's RAM (or picture), for desync checks (D12). Equal on every client that's in sync. */
    public long checksum() throws IOException {
        return ByteBuffer.wrap(call(CHECKSUM, new byte[0])).order(ByteOrder.LITTLE_ENDIAN).getLong();
    }

    private byte[] call(int command, byte[] payload) throws IOException {
        ByteBuffer head = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN).putInt(command).putInt(payload.length);
        toHost.write(head.array());
        toHost.write(payload);
        toHost.flush();
        ByteBuffer reply = ByteBuffer.wrap(readFully(8)).order(ByteOrder.LITTLE_ENDIAN);
        int status = reply.getInt();
        byte[] data = readFully(reply.getInt());
        if (status != 0) throw new HostException(new String(data, StandardCharsets.UTF_8));
        return data;
    }

    private byte[] readFully(int length) throws IOException {
        byte[] data = fromHost.readNBytes(length);
        if (data.length < length) throw new EOFException("cartridge-host exited");
        return data;
    }

    @Override
    public void close() {
        try {
            if (process.isAlive()) call(SHUTDOWN, new byte[0]);
        } catch (IOException ignored) {
            // it may already be gone; it's destroyed below either way
        }
        try {
            toHost.close();
            if (!process.waitFor(5, TimeUnit.SECONDS)) process.destroyForcibly().waitFor(5, TimeUnit.SECONDS);
        } catch (IOException | InterruptedException e) {
            process.destroyForcibly();
            if (e instanceof InterruptedException) Thread.currentThread().interrupt();
        }
        shm.close();
        try {
            Files.deleteIfExists(shmFile);
        } catch (IOException ignored) {
            // Windows keeps the file while a mapping lives; SharedMemory.cleanStale removes it later
        }
    }
}
