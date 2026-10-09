package com.nanobuntv.cartridge.net;

import com.nanobuntv.cartridge.Cartridge;
import com.nanobuntv.cartridge.netplay.GameKey;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Client to server: this client stopped emulating the console (out of range, powered off, left). */
public record SessionLeavePayload(GameKey game, int bootId) implements CustomPacketPayload {
    public static final Type<SessionLeavePayload> TYPE = new Type<>(Cartridge.id("session_leave"));
    public static final StreamCodec<ByteBuf, SessionLeavePayload> STREAM_CODEC = StreamCodec.composite(
            GameKey.STREAM_CODEC, SessionLeavePayload::game,
            ByteBufCodecs.INT, SessionLeavePayload::bootId,
            SessionLeavePayload::new);

    @Override
    public Type<SessionLeavePayload> type() {
        return TYPE;
    }
}
