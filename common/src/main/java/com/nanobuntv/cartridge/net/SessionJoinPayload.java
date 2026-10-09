package com.nanobuntv.cartridge.net;

import com.nanobuntv.cartridge.Cartridge;
import com.nanobuntv.cartridge.netplay.GameKey;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Client to server: this client has the console's game loaded and wants to join it (D12, D13). {@code core} is the
 * SHA-256 of its core, checked against the session's (D58, D89); {@code fps} is the core's frame rate.
 */
public record SessionJoinPayload(GameKey game, int bootId, String core, double fps) implements CustomPacketPayload {
    public static final Type<SessionJoinPayload> TYPE = new Type<>(Cartridge.id("session_join"));
    public static final StreamCodec<ByteBuf, SessionJoinPayload> STREAM_CODEC = StreamCodec.composite(
            GameKey.STREAM_CODEC, SessionJoinPayload::game,
            ByteBufCodecs.INT, SessionJoinPayload::bootId,
            ByteBufCodecs.stringUtf8(64), SessionJoinPayload::core,
            ByteBufCodecs.DOUBLE, SessionJoinPayload::fps,
            SessionJoinPayload::new);

    @Override
    public Type<SessionJoinPayload> type() {
        return TYPE;
    }
}
