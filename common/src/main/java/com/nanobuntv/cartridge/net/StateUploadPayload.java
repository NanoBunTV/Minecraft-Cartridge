package com.nanobuntv.cartridge.net;

import com.nanobuntv.cartridge.Cartridge;
import com.nanobuntv.cartridge.netplay.GameKey;
import com.nanobuntv.cartridge.item.MemoryCardContents;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Client to server: a piece of a savestate taken before {@code frame}, answering a {@link StateRequestPayload}
 * ({@code requestId}), or {@link #LEAVING} from the last client to leave (D90). Pieces arrive in order. The first
 * piece also carries a screenshot of the picture at the time, for a Memory Card saved from it (item/MemoryCardContents,
 * D98); later pieces carry none.
 */
public record StateUploadPayload(GameKey game, int bootId, int requestId, int frame, int total, int offset,
                                 byte[] data, byte[] thumbnail) implements CustomPacketPayload {
    /** Vanilla caps serverbound custom payloads at 32 KiB, and the screenshot rides along with the first piece. */
    public static final int MAX_CHUNK = 24 * 1024;
    public static final int LEAVING = -1;

    public static final Type<StateUploadPayload> TYPE = new Type<>(Cartridge.id("state_upload"));
    public static final StreamCodec<FriendlyByteBuf, StateUploadPayload> STREAM_CODEC =
            CustomPacketPayload.codec(StateUploadPayload::write, StateUploadPayload::read);

    private void write(FriendlyByteBuf buf) {
        GameKey.STREAM_CODEC.encode(buf, game);
        buf.writeInt(bootId);
        buf.writeInt(requestId);
        buf.writeVarInt(frame);
        buf.writeVarInt(total);
        buf.writeVarInt(offset);
        buf.writeByteArray(data);
        buf.writeByteArray(thumbnail);
    }

    private static StateUploadPayload read(FriendlyByteBuf buf) {
        return new StateUploadPayload(GameKey.STREAM_CODEC.decode(buf), buf.readInt(), buf.readInt(), buf.readVarInt(), buf.readVarInt(),
                buf.readVarInt(), buf.readByteArray(MAX_CHUNK), buf.readByteArray(MemoryCardContents.THUMB_BYTES));
    }

    @Override
    public Type<StateUploadPayload> type() {
        return TYPE;
    }
}
