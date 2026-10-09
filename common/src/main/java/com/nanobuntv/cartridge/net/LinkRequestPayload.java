package com.nanobuntv.cartridge.net;

import com.nanobuntv.cartridge.Cartridge;
import io.netty.buffer.ByteBuf;
import java.util.UUID;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Client to server: "Link with..." picked this player (D176); the server asks them. */
public record LinkRequestPayload(UUID target) implements CustomPacketPayload {
    public static final Type<LinkRequestPayload> TYPE = new Type<>(Cartridge.id("link_request"));
    public static final StreamCodec<ByteBuf, LinkRequestPayload> STREAM_CODEC =
            UUIDUtil.STREAM_CODEC.map(LinkRequestPayload::new, LinkRequestPayload::target);

    @Override
    public Type<LinkRequestPayload> type() {
        return TYPE;
    }
}
