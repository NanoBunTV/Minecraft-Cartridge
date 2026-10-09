package com.nanobuntv.cartridge.net;

import com.nanobuntv.cartridge.Cartridge;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Client to server: a piece of a {@code .sav} file to become the battery save of the cart in the player's open
 * Cartridge Writer (D97). Pieces arrive in order; the last one completes it. The server answers with a
 * {@link SaveFilePayload}.
 */
public record SaveImportPayload(int total, int offset, byte[] data) implements CustomPacketPayload {
    public static final int MAX_CHUNK = 24 * 1024;

    public static final Type<SaveImportPayload> TYPE = new Type<>(Cartridge.id("save_import"));
    public static final StreamCodec<ByteBuf, SaveImportPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, SaveImportPayload::total,
            ByteBufCodecs.VAR_INT, SaveImportPayload::offset,
            ByteBufCodecs.byteArray(MAX_CHUNK), SaveImportPayload::data,
            SaveImportPayload::new);

    @Override
    public Type<SaveImportPayload> type() {
        return TYPE;
    }
}
