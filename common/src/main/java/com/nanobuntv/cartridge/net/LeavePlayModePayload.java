package com.nanobuntv.cartridge.net;

import com.nanobuntv.cartridge.Cartridge;
import com.nanobuntv.cartridge.netplay.GameKey;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Client to server: the player left play mode (Esc, D22) and lets go of their pad on this console. */
public record LeavePlayModePayload(GameKey game) implements CustomPacketPayload {
    public static final Type<LeavePlayModePayload> TYPE = new Type<>(Cartridge.id("leave_play_mode"));
    public static final StreamCodec<ByteBuf, LeavePlayModePayload> STREAM_CODEC =
            GameKey.STREAM_CODEC.map(LeavePlayModePayload::new, LeavePlayModePayload::game);

    @Override
    public Type<LeavePlayModePayload> type() {
        return TYPE;
    }
}
