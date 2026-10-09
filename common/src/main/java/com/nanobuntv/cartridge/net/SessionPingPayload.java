package com.nanobuntv.cartridge.net;

import com.nanobuntv.cartridge.Cartridge;
import com.nanobuntv.cartridge.netplay.GameKey;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Client to server, about once a second: answered at once with a {@link SessionPongPayload}, which times the round
 * trip and the session clock. {@code rttMs} is the client's latest measured round trip, for the server's leads and
 * the players' input delay (D83).
 */
public record SessionPingPayload(GameKey game, int bootId, int nonce, int rttMs) implements CustomPacketPayload {
    public static final Type<SessionPingPayload> TYPE = new Type<>(Cartridge.id("session_ping"));
    public static final StreamCodec<ByteBuf, SessionPingPayload> STREAM_CODEC = StreamCodec.composite(
            GameKey.STREAM_CODEC, SessionPingPayload::game,
            ByteBufCodecs.INT, SessionPingPayload::bootId,
            ByteBufCodecs.INT, SessionPingPayload::nonce,
            ByteBufCodecs.VAR_INT, SessionPingPayload::rttMs,
            SessionPingPayload::new);

    @Override
    public Type<SessionPingPayload> type() {
        return TYPE;
    }
}
