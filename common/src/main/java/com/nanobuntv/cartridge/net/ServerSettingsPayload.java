package com.nanobuntv.cartridge.net;

import com.nanobuntv.cartridge.Cartridge;
import com.nanobuntv.cartridge.config.ServerSettings;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Server to client: the server's settings (D123), on join and whenever they change. */
public record ServerSettingsPayload(ServerSettings settings) implements CustomPacketPayload {
    public static final Type<ServerSettingsPayload> TYPE = new Type<>(Cartridge.id("server_settings"));
    public static final StreamCodec<ByteBuf, ServerSettingsPayload> STREAM_CODEC =
            ServerSettings.STREAM_CODEC.map(ServerSettingsPayload::new, ServerSettingsPayload::settings);

    @Override
    public Type<ServerSettingsPayload> type() {
        return TYPE;
    }
}
