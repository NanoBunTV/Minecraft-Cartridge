package com.nanobuntv.cartridge.net;

import com.nanobuntv.cartridge.Cartridge;
import com.nanobuntv.cartridge.emu.ConsoleKind;
import com.nanobuntv.cartridge.netplay.GameKey;
import io.netty.handler.codec.DecoderException;
import java.util.UUID;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import org.jetbrains.annotations.Nullable;

/**
 * Server to client: how the console's game stands (D12, D85, D88). {@code status} is one of netplay/SessionTimeline's
 * (attract, running, paused); per port, who plays it (null for nobody), the frame their inputs count from, and their
 * round trip in ms; how many clients emulate it; the present frame; and whose client is the authority ({@code source}:
 * its savestates and battery saves count, D85, D95).
 */
public record SessionStatePayload(GameKey game, int bootId, int status, double present, int members,
                                  UUID[] holders, int[] starts, int[] rtts, @Nullable UUID source)
        implements CustomPacketPayload {
    public static final Type<SessionStatePayload> TYPE = new Type<>(Cartridge.id("session_state"));
    public static final StreamCodec<FriendlyByteBuf, SessionStatePayload> STREAM_CODEC =
            CustomPacketPayload.codec(SessionStatePayload::write, SessionStatePayload::read);

    public int ports() {
        return holders.length;
    }

    @Nullable
    public UUID holder(int port) {
        return port < holders.length ? holders[port] : null;
    }

    public int start(int port) {
        return port < starts.length ? starts[port] : 0;
    }

    public int rtt(int port) {
        return port < rtts.length ? rtts[port] : 0;
    }

    private void write(FriendlyByteBuf buf) {
        GameKey.STREAM_CODEC.encode(buf, game);
        buf.writeInt(bootId);
        buf.writeByte(status);
        buf.writeDouble(present);
        buf.writeVarInt(members);
        buf.writeByte(holders.length);
        for (int p = 0; p < holders.length; p++) {
            buf.writeNullable(holders[p], (b, id) -> b.writeUUID(id));
            buf.writeVarInt(starts[p]);
            buf.writeVarInt(rtts[p]);
        }
        buf.writeNullable(source, (b, id) -> b.writeUUID(id));
    }

    private static SessionStatePayload read(FriendlyByteBuf buf) {
        GameKey game = GameKey.STREAM_CODEC.decode(buf);
        int bootId = buf.readInt(), status = buf.readByte();
        double present = buf.readDouble();
        int members = buf.readVarInt(), ports = buf.readByte();
        if (ports < 0 || ports > ConsoleKind.MAX_PORTS) throw new DecoderException("bad port count " + ports);
        UUID[] holders = new UUID[ports];
        int[] starts = new int[ports], rtts = new int[ports];
        for (int p = 0; p < ports; p++) {
            holders[p] = buf.readNullable(b -> b.readUUID());
            starts[p] = buf.readVarInt();
            rtts[p] = buf.readVarInt();
        }
        return new SessionStatePayload(game, bootId, status, present, members, holders, starts, rtts,
                buf.readNullable(b -> b.readUUID()));
    }

    @Override
    public Type<SessionStatePayload> type() {
        return TYPE;
    }
}
