package com.nanobuntv.cartridge.net;

import com.nanobuntv.cartridge.Cartridge;
import com.nanobuntv.cartridge.save.SaveStore;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Server to client: how a battery save import or export went (D97). {@link #EXPORT} carries the save and a file name
 * to suggest; {@link #IMPORTED} and {@link #FAILED} a message for the Writer screen.
 */
public record SaveFilePayload(int state, Component message, String fileName, byte[] data) implements CustomPacketPayload {
    public static final int EXPORT = 0, IMPORTED = 1, FAILED = 2;

    public static final Type<SaveFilePayload> TYPE = new Type<>(Cartridge.id("save_file"));
    public static final StreamCodec<RegistryFriendlyByteBuf, SaveFilePayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, SaveFilePayload::state,
            ComponentSerialization.STREAM_CODEC, SaveFilePayload::message,
            ByteBufCodecs.STRING_UTF8, SaveFilePayload::fileName,
            ByteBufCodecs.byteArray(SaveStore.MAX_SRAM), SaveFilePayload::data,
            SaveFilePayload::new);

    public static SaveFilePayload message(int state, Component message) {
        return new SaveFilePayload(state, message, "", new byte[0]);
    }

    @Override
    public Type<SaveFilePayload> type() {
        return TYPE;
    }
}
