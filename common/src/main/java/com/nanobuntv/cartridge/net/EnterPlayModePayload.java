package com.nanobuntv.cartridge.net;

import com.nanobuntv.cartridge.Cartridge;
import com.nanobuntv.cartridge.netplay.GameKey;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Server to client: you now hold the pad in {@code port} of this console; enter play mode (D19, D20). */
public record EnterPlayModePayload(GameKey game, int port) implements CustomPacketPayload {
    public static final Type<EnterPlayModePayload> TYPE = new Type<>(Cartridge.id("enter_play_mode"));
    public static final StreamCodec<ByteBuf, EnterPlayModePayload> STREAM_CODEC = StreamCodec.composite(
            GameKey.STREAM_CODEC, EnterPlayModePayload::game,
            ByteBufCodecs.VAR_INT, EnterPlayModePayload::port,
            EnterPlayModePayload::new);

    @Override
    public Type<EnterPlayModePayload> type() {
        return TYPE;
    }
}
