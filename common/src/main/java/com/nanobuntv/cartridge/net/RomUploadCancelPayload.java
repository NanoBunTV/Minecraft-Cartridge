package com.nanobuntv.cartridge.net;

import com.nanobuntv.cartridge.Cartridge;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Client to server: drop this player's upload. */
public record RomUploadCancelPayload() implements CustomPacketPayload {
    public static final RomUploadCancelPayload INSTANCE = new RomUploadCancelPayload();
    public static final Type<RomUploadCancelPayload> TYPE = new Type<>(Cartridge.id("rom_upload_cancel"));
    public static final StreamCodec<ByteBuf, RomUploadCancelPayload> STREAM_CODEC = StreamCodec.unit(INSTANCE);

    @Override
    public Type<RomUploadCancelPayload> type() {
        return TYPE;
    }
}
