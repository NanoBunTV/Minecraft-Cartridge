package com.nanobuntv.cartridge.net;

import com.nanobuntv.cartridge.Cartridge;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Server to client: a piece of a requested ROM or BIOS. {@code total} is the whole file's size, or -1 if the server
 * doesn't have it. The client answers each piece with a {@link RomAckPayload}.
 */
public record RomDataPayload(int kind, String sha1, int total, int offset, byte[] data) implements CustomPacketPayload {
    public static final int MAX_CHUNK = 128 * 1024;

    public static final Type<RomDataPayload> TYPE = new Type<>(Cartridge.id("rom_data"));
    public static final StreamCodec<ByteBuf, RomDataPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, RomDataPayload::kind,
            ByteBufCodecs.stringUtf8(40), RomDataPayload::sha1,
            ByteBufCodecs.VAR_INT, RomDataPayload::total,
            ByteBufCodecs.VAR_INT, RomDataPayload::offset,
            ByteBufCodecs.byteArray(MAX_CHUNK), RomDataPayload::data,
            RomDataPayload::new);

    @Override
    public Type<RomDataPayload> type() {
        return TYPE;
    }
}
