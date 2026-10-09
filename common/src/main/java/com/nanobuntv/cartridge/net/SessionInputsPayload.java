package com.nanobuntv.cartridge.net;

import com.nanobuntv.cartridge.Cartridge;
import com.nanobuntv.cartridge.netplay.GameKey;
import com.nanobuntv.cartridge.netplay.Segment;
import java.util.List;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Server to client: inputs for the console's game (each port's run continues where the last one ended), events
 * stamped onto frames ({@code eventFrames[i]} gets {@code eventFlags[i]}, see netplay/SessionTimeline), and the
 * session's present frame when this was sent.
 */
public record SessionInputsPayload(GameKey game, int bootId, double present, List<Segment> segments,
                                   int[] eventFrames, int[] eventFlags) implements CustomPacketPayload {
    private static final int MAX_EVENTS = 4096;

    public static final Type<SessionInputsPayload> TYPE = new Type<>(Cartridge.id("session_inputs"));
    public static final StreamCodec<FriendlyByteBuf, SessionInputsPayload> STREAM_CODEC =
            CustomPacketPayload.codec(SessionInputsPayload::write, SessionInputsPayload::read);

    private void write(FriendlyByteBuf buf) {
        GameKey.STREAM_CODEC.encode(buf, game);
        buf.writeInt(bootId);
        buf.writeDouble(present);
        NetCodecs.writeSegments(buf, segments);
        NetCodecs.writeInts(buf, eventFrames);
        NetCodecs.writeInts(buf, eventFlags);
    }

    private static SessionInputsPayload read(FriendlyByteBuf buf) {
        GameKey game = GameKey.STREAM_CODEC.decode(buf);
        int bootId = buf.readInt();
        double present = buf.readDouble();
        List<Segment> segments = NetCodecs.readSegments(buf);
        int[] frames = NetCodecs.readInts(buf, MAX_EVENTS);
        int[] flags = NetCodecs.readInts(buf, MAX_EVENTS);
        if (frames.length != flags.length) throw new io.netty.handler.codec.DecoderException("event lists differ");
        return new SessionInputsPayload(game, bootId, present, segments, frames, flags);
    }

    @Override
    public Type<SessionInputsPayload> type() {
        return TYPE;
    }
}
