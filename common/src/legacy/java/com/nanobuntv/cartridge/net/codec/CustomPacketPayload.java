package com.nanobuntv.cartridge.net.codec;

import net.minecraft.resources.ResourceLocation;

/**
 * Minecraft's {@code CustomPacketPayload} (1.20.5), for the versions before it: a payload knows its type, the type is
 * its channel id. The loaders' network helpers send each one on its own channel with its codec.
 */
public interface CustomPacketPayload {
    Type<? extends CustomPacketPayload> type();

    record Type<T extends CustomPacketPayload>(ResourceLocation id) {}

    static <B, T extends CustomPacketPayload> StreamCodec<B, T> codec(StreamCodec.MemberEncoder<B, T> encoder,
            StreamCodec.Decoder<B, T> decoder) {
        return StreamCodec.ofMember(encoder, decoder);
    }
}
