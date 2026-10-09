package com.nanobuntv.cartridge.net;

import com.nanobuntv.cartridge.Cartridge;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Client to server: the next piece of an upload. Small enough for vanilla's 32 KiB limit on serverbound custom
 * payloads; the server acknowledges each one, and the client keeps a fixed window in flight.
 */
public record RomUploadChunkPayload(int offset, byte[] data) implements CustomPacketPayload {
    public static final int MAX_CHUNK = 24 * 1024;

    public static final Type<RomUploadChunkPayload> TYPE = new Type<>(Cartridge.id("rom_upload_chunk"));
    public static final StreamCodec<ByteBuf, RomUploadChunkPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, RomUploadChunkPayload::offset,
            ByteBufCodecs.byteArray(MAX_CHUNK), RomUploadChunkPayload::data,
            RomUploadChunkPayload::new);

    @Override
    public Type<RomUploadChunkPayload> type() {
        return TYPE;
    }
}
