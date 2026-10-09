package com.nanobuntv.cartridge.net;

import com.nanobuntv.cartridge.Cartridge;
import com.nanobuntv.cartridge.netplay.GameKey;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Client to server: a piece of the game's battery RAM from the authority's client, once it settled after a change,
 * or as the game stops (D95). The server keeps it as the cartridge's battery save. Pieces arrive in order.
 */
public record SramUploadPayload(GameKey game, int bootId, int total, int offset, byte[] data)
        implements CustomPacketPayload {
    public static final int MAX_CHUNK = 24 * 1024;

    public static final Type<SramUploadPayload> TYPE = new Type<>(Cartridge.id("sram_upload"));
    public static final StreamCodec<ByteBuf, SramUploadPayload> STREAM_CODEC = StreamCodec.composite(
            GameKey.STREAM_CODEC, SramUploadPayload::game,
            ByteBufCodecs.INT, SramUploadPayload::bootId,
            ByteBufCodecs.VAR_INT, SramUploadPayload::total,
            ByteBufCodecs.VAR_INT, SramUploadPayload::offset,
            ByteBufCodecs.byteArray(MAX_CHUNK), SramUploadPayload::data,
            SramUploadPayload::new);

    @Override
    public Type<SramUploadPayload> type() {
        return TYPE;
    }
}
