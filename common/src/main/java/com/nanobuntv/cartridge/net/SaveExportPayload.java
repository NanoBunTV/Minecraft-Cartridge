package com.nanobuntv.cartridge.net;

import com.nanobuntv.cartridge.Cartridge;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Client to server: send me the battery save of the cart in my open Cartridge Writer (D97). The answer is a
 * {@link SaveFilePayload}.
 */
public record SaveExportPayload() implements CustomPacketPayload {
    public static final SaveExportPayload INSTANCE = new SaveExportPayload();
    public static final Type<SaveExportPayload> TYPE = new Type<>(Cartridge.id("save_export"));
    public static final StreamCodec<ByteBuf, SaveExportPayload> STREAM_CODEC = StreamCodec.unit(INSTANCE);

    @Override
    public Type<SaveExportPayload> type() {
        return TYPE;
    }
}
