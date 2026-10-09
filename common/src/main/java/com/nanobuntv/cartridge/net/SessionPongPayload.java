package com.nanobuntv.cartridge.net;

import com.nanobuntv.cartridge.Cartridge;
import com.nanobuntv.cartridge.netplay.GameKey;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Server to client: the answer to a {@link SessionPingPayload}, with the session's present frame when it was sent. */
public record SessionPongPayload(GameKey game, int bootId, int nonce, double present) implements CustomPacketPayload {
    public static final Type<SessionPongPayload> TYPE = new Type<>(Cartridge.id("session_pong"));
    public static final StreamCodec<ByteBuf, SessionPongPayload> STREAM_CODEC = StreamCodec.composite(
            GameKey.STREAM_CODEC, SessionPongPayload::game,
            ByteBufCodecs.INT, SessionPongPayload::bootId,
            ByteBufCodecs.INT, SessionPongPayload::nonce,
            ByteBufCodecs.DOUBLE, SessionPongPayload::present,
            SessionPongPayload::new);

    @Override
    public Type<SessionPongPayload> type() {
        return TYPE;
    }
}
