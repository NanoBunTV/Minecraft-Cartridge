package com.nanobuntv.cartridge.net;

import com.nanobuntv.cartridge.Cartridge;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Client to server: send me this ROM or BIOS ({@link RomUploadStartPayload#KIND_ROM} or {@code KIND_BIOS}), which a
 * console in range needs and this client hasn't cached (D16). Answered with {@link RomDataPayload}s.
 */
public record RomRequestPayload(int kind, String sha1) implements CustomPacketPayload {
    public static final Type<RomRequestPayload> TYPE = new Type<>(Cartridge.id("rom_request"));
    public static final StreamCodec<ByteBuf, RomRequestPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, RomRequestPayload::kind,
            ByteBufCodecs.stringUtf8(40), RomRequestPayload::sha1,
            RomRequestPayload::new);

    @Override
    public Type<RomRequestPayload> type() {
        return TYPE;
    }
}
