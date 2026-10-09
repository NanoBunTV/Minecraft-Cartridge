package com.nanobuntv.cartridge.net;

import com.nanobuntv.cartridge.Cartridge;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Client to server: the player wants to burn a game (or upload a BIOS, D74) in the Cartridge Writer they have open.
 * {@code system} is the client's guess, used to ask for a missing BIOS before any data is sent; the server checks the
 * real header once the file has arrived. Answered with {@link RomUploadStatusPayload}.
 */
public record RomUploadStartPayload(int kind, String system, String fileName, String title, int size, String sha1)
        implements CustomPacketPayload {
    /** KIND_RESTORE (D124): the missing game file of the burned cart in the Writer, which must have its SHA-1. */
    public static final int KIND_ROM = 0, KIND_BIOS = 1, KIND_RESTORE = 2;
    public static final int MAX_TITLE = 50;

    public static final Type<RomUploadStartPayload> TYPE = new Type<>(Cartridge.id("rom_upload_start"));
    public static final StreamCodec<ByteBuf, RomUploadStartPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, RomUploadStartPayload::kind,
            ByteBufCodecs.stringUtf8(16), RomUploadStartPayload::system,
            ByteBufCodecs.stringUtf8(256), RomUploadStartPayload::fileName,
            ByteBufCodecs.stringUtf8(MAX_TITLE * 4), RomUploadStartPayload::title,
            ByteBufCodecs.VAR_INT, RomUploadStartPayload::size,
            ByteBufCodecs.stringUtf8(40), RomUploadStartPayload::sha1,
            RomUploadStartPayload::new);

    @Override
    public Type<RomUploadStartPayload> type() {
        return TYPE;
    }
}
