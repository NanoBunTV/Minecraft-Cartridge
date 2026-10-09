package com.nanobuntv.cartridge.emu;

import java.io.IOException;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.invoke.VarHandle;
import java.lang.reflect.Field;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

/**
 * The client side of a host's shared-memory file (layout in sidecar/src/protocol.h): the latest video frame as
 * RGBA8888 behind a seqlock, and an int16 stereo audio ring. A memory-mapped file rather than a named shared-memory
 * object, because Java 21 can only map files without preview APIs.
 * <p>
 * Thread-safe: reads and {@link #close()} are synchronized, and nothing touches the mapping once it's closed (an
 * access after unmapping would crash the JVM).
 */
public final class SharedMemory implements AutoCloseable {
    public static final int MAGIC = 0x4D534843; // "CHSM"
    public static final int LAYOUT_VERSION = 1;
    public static final int SIZE = 4096 + 640 * 480 * 4 + 16384 * 4;
    public static final String FILE_SUFFIX = ".shm";

    private static final VarHandle LONG = MethodHandles.byteBufferViewVarHandle(long[].class, ByteOrder.LITTLE_ENDIAN);
    private static final int VIDEO_SEQ = 32, FRAME_COUNT = 40, WIDTH = 48, HEIGHT = 52, AUDIO_WRITE = 64, FPS = 72,
            SAMPLE_RATE = 80;

    /** A copied video frame: {@code width * height * 4} bytes of RGBA were written to the target buffer. */
    public record Frame(int width, int height, long sequence) {}

    /** Unsafe.invokeCleaner: unmaps now instead of at GC, so Windows can delete the file (null if unavailable). */
    private static final MethodHandle UNMAP = findUnmap();

    private final MappedByteBuffer buffer;
    private final int videoOffset, audioOffset, audioCapacity;
    private boolean closed;

    private SharedMemory(MappedByteBuffer buffer) throws IOException {
        this.buffer = buffer;
        buffer.order(ByteOrder.LITTLE_ENDIAN);
        if (buffer.getInt(0) != MAGIC || buffer.getInt(4) != LAYOUT_VERSION) {
            throw new IOException("unexpected shared-memory layout");
        }
        videoOffset = buffer.getInt(12);
        audioOffset = buffer.getInt(20);
        audioCapacity = buffer.getInt(24);
    }

    static SharedMemory open(Path file) throws IOException {
        try (FileChannel channel = FileChannel.open(file, StandardOpenOption.READ, StandardOpenOption.WRITE)) {
            MappedByteBuffer mapped = channel.map(FileChannel.MapMode.READ_WRITE, 0, SIZE);
            try {
                return new SharedMemory(mapped);
            } catch (IOException e) {
                unmap(mapped);
                throw e;
            }
        }
    }

    /**
     * Where hosts' files go: /dev/shm on Linux (memory, no disk writes) when present, else
     * {@code <gameDir>/cartridge_mod/run} (the host marks its file temporary on Windows, which keeps it in memory).
     */
    public static Path directory(Path gameDir) {
        Path devShm = Path.of("/dev/shm");
        if (HostPlatform.current().orElse(null) == HostPlatform.LINUX_X86_64 && Files.isDirectory(devShm)) {
            return devShm;
        }
        return gameDir.resolve("cartridge_mod").resolve("run");
    }

    /** Removes files left by earlier sessions (Windows can't delete a file while a mapping of it lives). */
    public static void cleanStale(Path dir, String prefix) {
        if (!Files.isDirectory(dir)) return;
        try (DirectoryStream<Path> files = Files.newDirectoryStream(dir, prefix + "*" + FILE_SUFFIX)) {
            for (Path f : files) {
                try {
                    Files.deleteIfExists(f);
                } catch (IOException ignored) {
                    // still mapped by a live host: leave it
                }
            }
        } catch (IOException ignored) {
            // nothing to clean
        }
    }

    /** The sequence of the last complete frame; it grows by 2 per frame, so compare it to see if a new one arrived. */
    public synchronized long frameSequence() {
        return closed ? 0 : (long) LONG.getAcquire(buffer, VIDEO_SEQ) & ~1L;
    }

    /**
     * Copies the latest complete frame into {@code target} (at least 640 * 480 * 4 bytes, written from position 0).
     * Returns null if the host kept writing over it (try again next client frame) or if this is closed.
     */
    public synchronized Frame readFrame(ByteBuffer target) {
        if (closed) return null;
        for (int attempt = 0; attempt < 4; attempt++) {
            long before = (long) LONG.getAcquire(buffer, VIDEO_SEQ);
            if ((before & 1) != 0) {
                Thread.onSpinWait();
                continue;
            }
            int width = buffer.getInt(WIDTH), height = buffer.getInt(HEIGHT);
            int bytes = width * height * 4;
            if (bytes <= 0 || bytes > SIZE - videoOffset) return null;
            target.clear();
            target.put(0, buffer, videoOffset, bytes);
            VarHandle.acquireFence();
            if ((long) LONG.getOpaque(buffer, VIDEO_SEQ) == before) return new Frame(width, height, before);
        }
        return null;
    }

    /** Stereo frames written so far (monotonic); pass it back to {@link #readAudio} as the next start. */
    public synchronized long audioPosition() {
        return closed ? 0 : (long) LONG.getAcquire(buffer, AUDIO_WRITE);
    }

    /**
     * Copies stereo frames from position {@code from} into {@code out} (interleaved L, R). If {@code from} fell
     * more than the ring's capacity behind, the oldest frames are skipped. Returns the position after the last frame
     * copied.
     */
    public synchronized long readAudio(long from, short[] out) {
        if (closed) return from;
        long end = audioPosition();
        long start = Math.max(from, end - audioCapacity);
        long count = Math.max(0, Math.min(end - start, out.length / 2));
        for (long i = 0; i < count; i++) {
            int slot = audioOffset + (int) ((start + i) & (audioCapacity - 1)) * 4;
            out[(int) i * 2] = buffer.getShort(slot);
            out[(int) i * 2 + 1] = buffer.getShort(slot + 2);
        }
        return start + count;
    }

    @Override
    public synchronized void close() {
        if (closed) return;
        closed = true;
        unmap(buffer);
    }

    private static void unmap(MappedByteBuffer mapped) {
        if (UNMAP == null) return;
        try {
            UNMAP.invokeExact((ByteBuffer) mapped);
        } catch (Throwable ignored) {
            // left to GC; the file is then removed by cleanStale later
        }
    }

    private static MethodHandle findUnmap() {
        try {
            Class<?> unsafeClass = Class.forName("sun.misc.Unsafe");
            Field theUnsafe = unsafeClass.getDeclaredField("theUnsafe");
            theUnsafe.setAccessible(true);
            return MethodHandles.lookup()
                    .findVirtual(unsafeClass, "invokeCleaner", MethodType.methodType(void.class, ByteBuffer.class))
                    .bindTo(theUnsafe.get(null));
        } catch (ReflectiveOperationException | RuntimeException e) {
            return null;
        }
    }
}
