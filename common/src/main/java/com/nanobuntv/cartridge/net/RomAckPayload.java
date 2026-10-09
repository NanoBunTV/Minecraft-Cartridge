package com.nanobuntv.cartridge.net;

import com.nanobuntv.cartridge.Cartridge;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Client to server: I have the first {@code received} bytes of this download; send on. */
public record RomAckPayload(String sha1, int received) implements CustomPacketPayload {
    public static final Type<RomAckPayload> TYPE = new Type<>(Cartridge.id("rom_ack"));
    public static final StreamCodec<ByteBuf, RomAckPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.stringUtf8(40), RomAckPayload::sha1,
            ByteBufCodecs.VAR_INT, RomAckPayload::received,
            RomAckPayload::new);

    @Override
    public Type<RomAckPayload> type() {
        return TYPE;
    }
}
