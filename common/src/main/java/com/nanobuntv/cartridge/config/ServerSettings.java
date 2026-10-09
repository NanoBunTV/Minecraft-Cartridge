package com.nanobuntv.cartridge.config;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;

/**
 * The server's settings (D122): {@code config/cartridge_mod-server.json5}, read by the server (or the integrated
 * one) and sent to each player on join (D123, {@link CartridgeConfig#synced}). Every field has its allowed range in
 * {@link #clamp}, which the config screen's sliders use too.
 */
public final class ServerSettings {
    public static final int SPECTATOR_MIN = 1, SPECTATOR_MAX = 128;
    public static final int ROM_SIZE_MIN = 1, ROM_SIZE_MAX = 256;
    public static final int QUOTA_MIN = 1, QUOTA_MAX = 1024 * 1024;
    public static final int PERMISSION_MIN = 0, PERMISSION_MAX = 4;
    public static final int CABLE_MIN = 1, CABLE_MAX = 64;
    public static final int TVS_MIN = 1, TVS_MAX = 64;
    public static final int ROLLBACK_MIN = 2, ROLLBACK_MAX = 60;
    public static final int DELAY_MIN = 0, DELAY_MAX = 10;
    public static final int SNAPSHOT_MIN = 5, SNAPSHOT_MAX = 3600;
    public static final int GUN_MIN = 2, GUN_MAX = 64;
    public static final int HANDHELD_MIN = 1, HANDHELD_MAX = 64;
    public static final int LINK_MIN = 2, LINK_MAX = 64;

    @SerialEntry(comment = "Players within this many blocks of a running TV emulate it and see and hear the game (D13).")
    public int spectatorRadius = 32;
    @SerialEntry(comment = "Largest ROM file players may upload, in megabytes (D16, D72, D115).")
    public int maxRomSizeMegabytes = 64;
    @SerialEntry(comment = "Total size of all stored ROMs, in megabytes; uploads are refused once it's reached (D71).")
    public int romStorageQuotaMegabytes = 4096;
    @SerialEntry(comment = "Permission level to burn games, upload a BIOS and import saves (D76, D97): 0 everyone, 2 operators.")
    public int uploadPermissionLevel = 0;
    @SerialEntry(comment = "Reject uploads whose header doesn't match the console's format (D16).")
    public boolean validateRomHeaders = true;
    @SerialEntry(comment = "How far apart, in blocks, a console and a TV linked by an AV Cable may be (D61).")
    public int cableRange = 16;
    @SerialEntry(comment = "How many TVs one console can feed (D61).")
    public int maxTvsPerConsole = 8;
    @SerialEntry(comment = "How many frames a player's game may run ahead of the other player's inputs (D83). 12 is about 200 ms.")
    public int rollbackFrames = 12;
    @SerialEntry(comment = "The most a player's own presses are held back on a slow link, in frames (D83).")
    public int maxInputDelayFrames = 3;
    @SerialEntry(comment = "How often a running game's savestate is saved, in seconds, so it resumes after a crash or restart (D90).")
    public int resumeSnapshotSeconds = 60;
    @SerialEntry(comment = "How far, in blocks, the Pixelon Blaster hits a TV from (D100).")
    public int lightGunRange = 16;
    @SerialEntry(comment = "Players within this many blocks of someone holding a running Pocketron see its screen (D160).")
    public int handheldRange = 8;
    @SerialEntry(comment = "How far apart, in blocks, two players with linked Pocketrons may be before the cable comes out (D178).")
    public int linkCableRange = 10;

    /** What a player is sent on join (D123); field by field, in this order. */
    public static final StreamCodec<ByteBuf, ServerSettings> STREAM_CODEC = StreamCodec.of(
            (buf, s) -> {
                buf.writeInt(s.spectatorRadius);
                buf.writeInt(s.maxRomSizeMegabytes);
                buf.writeInt(s.romStorageQuotaMegabytes);
                buf.writeInt(s.uploadPermissionLevel);
                buf.writeBoolean(s.validateRomHeaders);
                buf.writeInt(s.cableRange);
                buf.writeInt(s.maxTvsPerConsole);
                buf.writeInt(s.rollbackFrames);
                buf.writeInt(s.maxInputDelayFrames);
                buf.writeInt(s.resumeSnapshotSeconds);
                buf.writeInt(s.lightGunRange);
                buf.writeInt(s.handheldRange);
                buf.writeInt(s.linkCableRange);
            },
            buf -> {
                ServerSettings s = new ServerSettings();
                s.spectatorRadius = buf.readInt();
                s.maxRomSizeMegabytes = buf.readInt();
                s.romStorageQuotaMegabytes = buf.readInt();
                s.uploadPermissionLevel = buf.readInt();
                s.validateRomHeaders = buf.readBoolean();
                s.cableRange = buf.readInt();
                s.maxTvsPerConsole = buf.readInt();
                s.rollbackFrames = buf.readInt();
                s.maxInputDelayFrames = buf.readInt();
                s.resumeSnapshotSeconds = buf.readInt();
                s.lightGunRange = buf.readInt();
                s.handheldRange = buf.readInt();
                s.linkCableRange = buf.readInt();
                return s.clamp();
            });

    public int maxRomSizeBytes() {
        return maxRomSizeMegabytes * 1024 * 1024;
    }

    /** Pulls every value into its range (a hand-edited file, or a server sending nonsense). */
    public ServerSettings clamp() {
        spectatorRadius = Math.clamp(spectatorRadius, SPECTATOR_MIN, SPECTATOR_MAX);
        maxRomSizeMegabytes = Math.clamp(maxRomSizeMegabytes, ROM_SIZE_MIN, ROM_SIZE_MAX);
        romStorageQuotaMegabytes = Math.clamp(romStorageQuotaMegabytes, QUOTA_MIN, QUOTA_MAX);
        uploadPermissionLevel = Math.clamp(uploadPermissionLevel, PERMISSION_MIN, PERMISSION_MAX);
        cableRange = Math.clamp(cableRange, CABLE_MIN, CABLE_MAX);
        maxTvsPerConsole = Math.clamp(maxTvsPerConsole, TVS_MIN, TVS_MAX);
        rollbackFrames = Math.clamp(rollbackFrames, ROLLBACK_MIN, ROLLBACK_MAX);
        maxInputDelayFrames = Math.clamp(maxInputDelayFrames, DELAY_MIN, DELAY_MAX);
        resumeSnapshotSeconds = Math.clamp(resumeSnapshotSeconds, SNAPSHOT_MIN, SNAPSHOT_MAX);
        lightGunRange = Math.clamp(lightGunRange, GUN_MIN, GUN_MAX);
        handheldRange = Math.clamp(handheldRange, HANDHELD_MIN, HANDHELD_MAX);
        linkCableRange = Math.clamp(linkCableRange, LINK_MIN, LINK_MAX);
        return this;
    }
}
