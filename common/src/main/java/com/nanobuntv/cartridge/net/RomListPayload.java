package com.nanobuntv.cartridge.net;

import com.nanobuntv.cartridge.Cartridge;
import io.netty.buffer.ByteBuf;
import java.util.List;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Server to client: which games (ROM SHA-1s) the server has (D125), for the "Game missing" tooltip and the Writer's
 * restore (D124). {@code replace}: {@code added} is the whole list (on join, after the startup check); otherwise a
 * change.
 */
public record RomListPayload(boolean replace, List<String> added, List<String> removed) implements CustomPacketPayload {
    public static final Type<RomListPayload> TYPE = new Type<>(Cartridge.id("rom_list"));
    private static final StreamCodec<ByteBuf, List<String>> HASHES = ByteBufCodecs.stringUtf8(40).apply(ByteBufCodecs.list());
    public static final StreamCodec<ByteBuf, RomListPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.BOOL, RomListPayload::replace,
            HASHES, RomListPayload::added,
            HASHES, RomListPayload::removed,
            RomListPayload::new);

    @Override
    public Type<RomListPayload> type() {
        return TYPE;
    }
}
