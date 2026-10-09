package com.nanobuntv.cartridge.net;

import com.nanobuntv.cartridge.Cartridge;
import com.nanobuntv.cartridge.netplay.GameKey;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Client to server: the hash of the game's RAM before {@code frame}, once that frame is final (desync checks, D12). */
public record ChecksumPayload(GameKey game, int bootId, int frame, long checksum) implements CustomPacketPayload {
    public static final Type<ChecksumPayload> TYPE = new Type<>(Cartridge.id("checksum"));
    public static final StreamCodec<ByteBuf, ChecksumPayload> STREAM_CODEC = StreamCodec.composite(
            GameKey.STREAM_CODEC, ChecksumPayload::game,
            ByteBufCodecs.INT, ChecksumPayload::bootId,
            ByteBufCodecs.VAR_INT, ChecksumPayload::frame,
            ByteBufCodecs.VAR_LONG, ChecksumPayload::checksum,
            ChecksumPayload::new);

    @Override
    public Type<ChecksumPayload> type() {
        return TYPE;
    }
}
