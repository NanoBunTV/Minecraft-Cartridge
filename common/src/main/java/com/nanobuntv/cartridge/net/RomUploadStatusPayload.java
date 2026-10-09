package com.nanobuntv.cartridge.net;

import com.nanobuntv.cartridge.Cartridge;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Server to client: how an upload stands. {@link #SEND}: the server has {@code received} bytes, send on from there
 * (also the answer to a start). The others end the upload, with a message for the Writer screen.
 */
public record RomUploadStatusPayload(int state, int received, Component message) implements CustomPacketPayload {
    public static final int SEND = 0, DONE = 1, NEED_BIOS = 2, BIOS_DONE = 3, REJECTED = 4;

    public static final Type<RomUploadStatusPayload> TYPE = new Type<>(Cartridge.id("rom_upload_status"));
    public static final StreamCodec<RegistryFriendlyByteBuf, RomUploadStatusPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, RomUploadStatusPayload::state,
            ByteBufCodecs.VAR_INT, RomUploadStatusPayload::received,
            ComponentSerialization.STREAM_CODEC, RomUploadStatusPayload::message,
            RomUploadStatusPayload::new);

    public static RomUploadStatusPayload send(int received) {
        return new RomUploadStatusPayload(SEND, received, Component.empty());
    }

    @Override
    public Type<RomUploadStatusPayload> type() {
        return TYPE;
    }
}
