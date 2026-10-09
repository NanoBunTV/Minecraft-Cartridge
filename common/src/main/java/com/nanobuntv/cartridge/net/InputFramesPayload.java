package com.nanobuntv.cartridge.net;

import com.nanobuntv.cartridge.Cartridge;
import com.nanobuntv.cartridge.netplay.GameKey;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Client to server: a player's inputs for frames {@code start} onwards, on the port they hold (D12). */
public record InputFramesPayload(GameKey game, int bootId, int port, int start, int[] buttons)
        implements CustomPacketPayload {
    public static final int MAX_FRAMES = 256;

    public static final Type<InputFramesPayload> TYPE = new Type<>(Cartridge.id("input_frames"));
    public static final StreamCodec<FriendlyByteBuf, InputFramesPayload> STREAM_CODEC =
            CustomPacketPayload.codec(InputFramesPayload::write, InputFramesPayload::read);

    private void write(FriendlyByteBuf buf) {
        GameKey.STREAM_CODEC.encode(buf, game);
        buf.writeInt(bootId);
        buf.writeByte(port);
        buf.writeVarInt(start);
        NetCodecs.writeInputs(buf, buttons);
    }

    private static InputFramesPayload read(FriendlyByteBuf buf) {
        return new InputFramesPayload(GameKey.STREAM_CODEC.decode(buf), buf.readInt(), buf.readByte(), buf.readVarInt(),
                NetCodecs.readInputs(buf, MAX_FRAMES));
    }

    @Override
    public Type<InputFramesPayload> type() {
        return TYPE;
    }
}
