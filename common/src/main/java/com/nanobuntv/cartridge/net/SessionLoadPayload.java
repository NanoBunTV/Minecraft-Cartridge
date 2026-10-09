package com.nanobuntv.cartridge.net;

import com.nanobuntv.cartridge.Cartridge;
import com.nanobuntv.cartridge.netplay.GameKey;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Server to client: a piece of the Memory Card savestate that replaces the game's state before {@code frame} (D92).
 * All of it arrives before the load event on that frame (netplay/SessionTimeline.EVENT_LOAD).
 */
public record SessionLoadPayload(GameKey game, int bootId, int frame, int total, int offset, byte[] data)
        implements CustomPacketPayload {
    public static final int MAX_CHUNK = 512 * 1024;

    public static final Type<SessionLoadPayload> TYPE = new Type<>(Cartridge.id("session_load"));
    public static final StreamCodec<ByteBuf, SessionLoadPayload> STREAM_CODEC = StreamCodec.composite(
            GameKey.STREAM_CODEC, SessionLoadPayload::game,
            ByteBufCodecs.INT, SessionLoadPayload::bootId,
            ByteBufCodecs.VAR_INT, SessionLoadPayload::frame,
            ByteBufCodecs.VAR_INT, SessionLoadPayload::total,
            ByteBufCodecs.VAR_INT, SessionLoadPayload::offset,
            ByteBufCodecs.byteArray(MAX_CHUNK), SessionLoadPayload::data,
            SessionLoadPayload::new);

    @Override
    public Type<SessionLoadPayload> type() {
        return TYPE;
    }
}
