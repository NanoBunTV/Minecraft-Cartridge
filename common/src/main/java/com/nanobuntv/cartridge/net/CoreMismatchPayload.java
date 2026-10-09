package com.nanobuntv.cartridge.net;

import com.nanobuntv.cartridge.Cartridge;
import com.nanobuntv.cartridge.netplay.GameKey;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Server to client: your core differs from the one the players already in this game run (D58, D114). The core comes
 * with the mod, so this means another version of Cartridge; this client can't join this game.
 */
public record CoreMismatchPayload(GameKey game, int bootId) implements CustomPacketPayload {
    public static final Type<CoreMismatchPayload> TYPE = new Type<>(Cartridge.id("core_mismatch"));
    public static final StreamCodec<ByteBuf, CoreMismatchPayload> STREAM_CODEC = StreamCodec.composite(
            GameKey.STREAM_CODEC, CoreMismatchPayload::game,
            ByteBufCodecs.INT, CoreMismatchPayload::bootId,
            CoreMismatchPayload::new);

    @Override
    public Type<CoreMismatchPayload> type() {
        return TYPE;
    }
}
