package com.nanobuntv.cartridge.client;

import com.nanobuntv.cartridge.Cartridge;
import com.nanobuntv.cartridge.emu.RomCache;
import com.nanobuntv.cartridge.net.RomAckPayload;
import com.nanobuntv.cartridge.net.RomDataPayload;
import com.nanobuntv.cartridge.net.RomRequestPayload;
import com.nanobuntv.cartridge.platform.Services;
import com.nanobuntv.cartridge.rom.RomFormats;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.LockSupport;
import java.util.function.BooleanSupplier;
import net.minecraft.client.Minecraft;

/**
 * Downloads of ROMs and BIOS files this client needs but hasn't cached (D16): a console in range holds a cartridge
 * whose ROM isn't in the {@link RomCache}, or a disk game needs the BIOS (D74). One download per file however many
 * consoles wait for it. Console threads wait in {@link #await}; payloads arrive on the render thread.
 */
final class RomDownloads {
    /** The server doesn't have the file (or the download broke). */
    static final class MissingException extends IOException {
        MissingException(String message) {
            super(message);
        }
    }

    private static final class Download {
        volatile int total = -1, received;
        volatile boolean done, failed;
        volatile byte[] data;
        /** Consoles waiting for it; changed only inside the map's compute calls for its key. */
        int waiters;
    }

    private static final Map<String, Download> DOWNLOADS = new ConcurrentHashMap<>();

    private RomDownloads() {}

    /** The client's copy of a BIOS file, in the core's system directory where the core looks for it. */
    static Path biosPath(Path gameDir, RomFormats.Bios bios) {
        return gameDir.resolve("cartridge_mod").resolve("system").resolve(bios.fileName());
    }

    static boolean hasBios(Path gameDir, RomFormats.Bios bios) {
        Path file = biosPath(gameDir, bios);
        try {
            return Files.isRegularFile(file) && Files.size(file) == bios.size()
                    && RomCache.sha1(Files.readAllBytes(file)).equals(bios.sha1());
        } catch (IOException e) {
            return false;
        }
    }

    /** Saves a BIOS file (checked by the caller) where the core finds it. */
    static void installBios(Path gameDir, RomFormats.Bios bios, byte[] data) throws IOException {
        Path file = biosPath(gameDir, bios);
        Files.createDirectories(file.getParent());
        Path temp = Files.createTempFile(file.getParent(), bios.fileName(), ".tmp");
        try {
            Files.write(temp, data);
            Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } finally {
            Files.deleteIfExists(temp);
        }
    }

    /**
     * Fetches a file from the server and waits for it (any thread but the render thread). Returns its bytes, already
     * stored: a ROM in the cache, a BIOS in the system directory.
     */
    static byte[] await(int kind, String sha1, BooleanSupplier cancelled) throws IOException, InterruptedException {
        boolean[] created = {false};
        Download download = DOWNLOADS.compute(sha1, (key, old) -> {
            Download d = old != null && !old.failed ? old : new Download(); // another console may have asked already
            created[0] = d != old;
            d.waiters++;
            return d;
        });
        if (created[0]) {
            Minecraft.getInstance().execute(() -> Services.NETWORK.sendToServer(new RomRequestPayload(kind, sha1)));
        }
        try {
            while (!download.done && !download.failed) {
                if (cancelled.getAsBoolean()) throw new InterruptedException();
                LockSupport.parkNanos(20_000_000L);
            }
            if (download.failed) throw new MissingException(sha1);
            return download.data;
        } finally {
            // The last one waiting lets go, finished or not: a cancelled download's data isn't kept until disconnect.
            DOWNLOADS.computeIfPresent(sha1, (key, d) -> d == download && --d.waiters == 0 ? null : d);
        }
    }

    /** 0..1, or -1 if nothing is known yet. */
    static float progress(String sha1) {
        Download download = DOWNLOADS.get(sha1);
        if (download == null || download.total <= 0) return -1;
        return Math.min(1, download.received / (float) download.total);
    }

    /** Render thread: a piece arrived. */
    static void onData(RomDataPayload payload) {
        Download download = DOWNLOADS.get(payload.sha1());
        if (download == null || download.done || download.failed) return;
        if (payload.total() < 0) {
            download.failed = true;
            return;
        }
        if (download.data == null) {
            download.data = new byte[payload.total()];
            download.total = payload.total();
        }
        byte[] data = download.data;
        if (payload.offset() != download.received || payload.total() != data.length
                || payload.data().length > data.length - payload.offset()) {
            download.failed = true;
            return;
        }
        System.arraycopy(payload.data(), 0, data, payload.offset(), payload.data().length);
        download.received += payload.data().length;
        Services.NETWORK.sendToServer(new RomAckPayload(payload.sha1(), download.received));
        if (download.received == data.length) {
            // Check and store it off the render thread: hashing a big ROM takes a moment.
            Path gameDir = Services.PLATFORM.gameDir();
            CompletableFuture.runAsync(() -> finish(gameDir, payload.sha1(), download));
        }
    }

    private static void finish(Path gameDir, String sha1, Download download) {
        try {
            if (!RomCache.sha1(download.data).equals(sha1)) throw new IOException("checksum mismatch");
            Optional<RomFormats.Bios> bios = RomFormats.biosBySha1(sha1);
            if (bios.isPresent()) installBios(gameDir, bios.get(), download.data);
            else RomCache.put(gameDir, download.data);
            download.done = true;
        } catch (IOException e) {
            Cartridge.LOGGER.warn("Download of {} failed", sha1, e);
            download.failed = true;
        }
    }

    /** Leaving the world: whatever is still on its way won't arrive. */
    static void clear() {
        DOWNLOADS.values().forEach(d -> d.failed = true);
        DOWNLOADS.clear();
    }
}
