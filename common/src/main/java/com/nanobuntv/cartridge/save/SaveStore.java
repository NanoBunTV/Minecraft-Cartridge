package com.nanobuntv.cartridge.save;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Optional;
import java.util.UUID;

/**
 * The server's saves (D24), under {@code <world>/cartridge_mod/}:
 * <ul>
 *   <li>{@code sram/<cart UUID>.sav}: a cartridge's battery save, the raw bytes other emulators use (D97), so it
 *   travels with the cart however it's traded.</li>
 *   <li>{@code memory_cards/<card UUID>.state}: a Memory Card's savestate (D91), with the game's ROM SHA-1 (D94) and
 *   the core it was made with (savestates don't carry across core builds, D89).</li>
 * </ul>
 * Files are replaced whole through a temp file, so a crash leaves the old save or the new one. Plain Java.
 */
public final class SaveStore {
    /** NES battery RAM is 8 KB, rarely 32 KB; this is plenty, and fits one payload to the client. */
    public static final int MAX_SRAM = 128 * 1024;
    private static final int CARD_MAGIC = 0x434D4344; // "CMCD"
    private static final int CARD_VERSION = 1;
    private static final int MAX_STATE = 64 * 1024 * 1024;

    /** A Memory Card's savestate: made from the game {@code romSha1} on core {@code core} (SHA-256). */
    public record CardState(String romSha1, String core, long savedAt, byte[] state) {}

    private final Path sramDir, cardDir;

    /** {@code modDir}: the world's {@code cartridge_mod} folder. */
    public SaveStore(Path modDir) {
        this.sramDir = modDir.resolve("sram");
        this.cardDir = modDir.resolve("memory_cards");
    }

    // --- Battery saves (D24, D95-D97) ------------------------------------------------------------------------------

    public Path sramFile(UUID cart) {
        return sramDir.resolve(cart + ".sav");
    }

    /** The cart's battery save; empty if it has none yet. */
    public Optional<byte[]> readSram(UUID cart) {
        Path file = sramFile(cart);
        try {
            if (!Files.isRegularFile(file) || Files.size(file) > MAX_SRAM) return Optional.empty();
            return Optional.of(Files.readAllBytes(file));
        } catch (IOException e) {
            return Optional.empty();
        }
    }

    public void writeSram(UUID cart, byte[] data) throws IOException {
        if (data.length == 0 || data.length > MAX_SRAM) throw new IOException("battery save of " + data.length + " bytes");
        replace(sramFile(cart), data);
    }

    /** A copied cart starts with the original's save as it is now (D73); after that they're separate. */
    public void copySram(UUID from, UUID to) throws IOException {
        Optional<byte[]> save = readSram(from);
        if (save.isPresent()) writeSram(to, save.get());
    }

    /** The cart was erased (D78): its save goes with it. */
    public void deleteSram(UUID cart) {
        delete(sramFile(cart));
    }

    // --- Memory Cards (D91-D94) ---------------------------------------------------------------------------------------

    public Path cardFile(UUID card) {
        return cardDir.resolve(card + ".state");
    }

    public void writeCard(UUID card, CardState state) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream(state.state().length + 256);
        try (DataOutputStream out = new DataOutputStream(bytes)) {
            out.writeInt(CARD_MAGIC);
            out.writeInt(CARD_VERSION);
            out.writeUTF(state.romSha1());
            out.writeUTF(state.core());
            out.writeLong(state.savedAt());
            out.writeInt(state.state().length);
            out.write(state.state());
        }
        replace(cardFile(card), bytes.toByteArray());
    }

    public Optional<CardState> readCard(UUID card) {
        Path file = cardFile(card);
        if (!Files.isRegularFile(file)) return Optional.empty();
        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(Files.readAllBytes(file)))) {
            if (in.readInt() != CARD_MAGIC || in.readInt() != CARD_VERSION) return Optional.empty();
            String romSha1 = in.readUTF();
            String core = in.readUTF();
            long savedAt = in.readLong();
            int size = in.readInt();
            if (size <= 0 || size > MAX_STATE) return Optional.empty();
            byte[] state = in.readNBytes(size);
            if (state.length != size) return Optional.empty();
            return Optional.of(new CardState(romSha1, core, savedAt, state));
        } catch (IOException e) {
            return Optional.empty();
        }
    }

    // --- Files ------------------------------------------------------------------------------------------------------

    private static void replace(Path file, byte[] data) throws IOException {
        Files.createDirectories(file.getParent());
        Path temp = Files.createTempFile(file.getParent(), file.getFileName().toString(), ".tmp");
        try {
            Files.write(temp, data);
            Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } finally {
            Files.deleteIfExists(temp);
        }
    }

    private static void delete(Path file) {
        try {
            Files.deleteIfExists(file);
        } catch (IOException ignored) {
            // nothing reads it without its cart, so a leftover is harmless
        }
    }
}
