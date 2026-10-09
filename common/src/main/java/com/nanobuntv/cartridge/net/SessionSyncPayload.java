package com.nanobuntv.cartridge.net;

import com.nanobuntv.cartridge.Cartridge;
import com.nanobuntv.cartridge.netplay.GameKey;
import com.nanobuntv.cartridge.save.SaveStore;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Server to client: start (or restart) the game from the savestate taken before {@code baseFrame}, sent in pieces;
 * {@code total} 0 means from power-on (frame 0, no state), with the cartridge's battery save as it was at power-on in
 * {@code sram} (empty for none, D24). The inputs since then follow as {@link SessionInputsPayload}s. Joining, catching
 * up after a desync, and resuming all go through this (D12, D90).
 */
public record SessionSyncPayload(GameKey game, int bootId, int baseFrame, int total, int offset, byte[] data,
                                 byte[] sram) implements CustomPacketPayload {
    public static final int MAX_CHUNK = 512 * 1024;

    public static final Type<SessionSyncPayload> TYPE = new Type<>(Cartridge.id("session_sync"));
    public static final StreamCodec<FriendlyByteBuf, SessionSyncPayload> STREAM_CODEC =
            CustomPacketPayload.codec(SessionSyncPayload::write, SessionSyncPayload::read);

    private void write(FriendlyByteBuf buf) {
        GameKey.STREAM_CODEC.encode(buf, game);
        buf.writeInt(bootId);
        buf.writeVarInt(baseFrame);
        buf.writeVarInt(total);
        buf.writeVarInt(offset);
        buf.writeByteArray(data);
        buf.writeByteArray(sram);
    }

    private static SessionSyncPayload read(FriendlyByteBuf buf) {
        return new SessionSyncPayload(GameKey.STREAM_CODEC.decode(buf), buf.readInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(),
                buf.readByteArray(MAX_CHUNK), buf.readByteArray(SaveStore.MAX_SRAM));
    }

    @Override
    public Type<SessionSyncPayload> type() {
        return TYPE;
    }
}
