package com.nanobuntv.cartridge.net;

import com.nanobuntv.cartridge.Cartridge;
import com.nanobuntv.cartridge.netplay.GameKey;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Server to client: send your newest final savestate ({@link StateUploadPayload} with this {@code requestId}), for a
 * player joining, a resync or the periodic resume snapshot (D12, D90).
 */
public record StateRequestPayload(GameKey game, int bootId, int requestId) implements CustomPacketPayload {
    public static final Type<StateRequestPayload> TYPE = new Type<>(Cartridge.id("state_request"));
    public static final StreamCodec<ByteBuf, StateRequestPayload> STREAM_CODEC = StreamCodec.composite(
            GameKey.STREAM_CODEC, StateRequestPayload::game,
            ByteBufCodecs.INT, StateRequestPayload::bootId,
            ByteBufCodecs.INT, StateRequestPayload::requestId,
            StateRequestPayload::new);

    @Override
    public Type<StateRequestPayload> type() {
        return TYPE;
    }
}
