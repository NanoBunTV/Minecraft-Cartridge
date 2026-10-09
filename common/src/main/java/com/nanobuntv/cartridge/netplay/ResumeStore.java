package com.nanobuntv.cartridge.netplay;

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
 * Resume snapshots (D24, D90): the newest savestate of each console's game, so a room everyone left, an unloaded
 * chunk or a server restart picks up where the game was. One file per console,
 * {@code <world>/cartridge_mod/resume/<dimension>/<x>_<y>_<z>.state}; it only counts for the same power-on and the same
 * cartridge. Plain Java.
 */
public final class ResumeStore {
    private static final int MAGIC = 0x4352534D; // "CRSM"
    private static final int VERSION = 1;
    private static final int MAX_STATE = 64 * 1024 * 1024;

    /** A saved game: its savestate before {@code frame}, made with core {@code core} (SHA-256). */
    public record Resume(int bootId, UUID cartId, int frame, boolean everHeld, String core, byte[] state) {}

    private ResumeStore() {}

    public static Path file(Path worldModDir, String dimensionNamespace, String dimensionPath, int x, int y, int z) {
        return worldModDir.resolve("resume").resolve(dimensionNamespace).resolve(dimensionPath)
                .resolve(x + "_" + y + "_" + z + ".state");
    }

    /** A handheld's (D164): it travels with the item, so it's kept by the handheld's id. */
    public static Path handheldFile(Path worldModDir, UUID handheld) {
        return worldModDir.resolve("resume_handheld").resolve(handheld + ".state");
    }

    public static void write(Path file, Resume resume) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream(resume.state().length + 256);
        try (DataOutputStream out = new DataOutputStream(bytes)) {
            out.writeInt(MAGIC);
            out.writeInt(VERSION);
            out.writeInt(resume.bootId());
            out.writeLong(resume.cartId().getMostSignificantBits());
            out.writeLong(resume.cartId().getLeastSignificantBits());
            out.writeInt(resume.frame());
            out.writeBoolean(resume.everHeld());
            out.writeUTF(resume.core());
            out.writeInt(resume.state().length);
            out.write(resume.state());
        }
        Files.createDirectories(file.getParent());
        Path temp = Files.createTempFile(file.getParent(), file.getFileName().toString(), ".tmp");
        try {
            Files.write(temp, bytes.toByteArray());
            Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } finally {
            Files.deleteIfExists(temp);
        }
    }

    /** The saved game, if there is one and it belongs to this power-on and cartridge. */
    public static Optional<Resume> read(Path file, int bootId, UUID cartId) {
        if (!Files.isRegularFile(file)) return Optional.empty();
        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(Files.readAllBytes(file)))) {
            if (in.readInt() != MAGIC || in.readInt() != VERSION) return Optional.empty();
            int savedBoot = in.readInt();
            UUID savedCart = new UUID(in.readLong(), in.readLong());
            int frame = in.readInt();
            boolean everHeld = in.readBoolean();
            String core = in.readUTF();
            int size = in.readInt();
            if (savedBoot != bootId || !savedCart.equals(cartId) || frame < 0 || size <= 0 || size > MAX_STATE) {
                return Optional.empty();
            }
            byte[] state = in.readNBytes(size);
            if (state.length != size) return Optional.empty();
            return Optional.of(new Resume(savedBoot, savedCart, frame, everHeld, core, state));
        } catch (IOException e) {
            return Optional.empty();
        }
    }

    public static void delete(Path file) {
        try {
            Files.deleteIfExists(file);
        } catch (IOException ignored) {
            // it's only ever read back for the same power-on, so a leftover is harmless
        }
    }
}
