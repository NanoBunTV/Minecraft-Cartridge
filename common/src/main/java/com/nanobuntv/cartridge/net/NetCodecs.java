package com.nanobuntv.cartridge.net;

import com.nanobuntv.cartridge.emu.ConsoleKind;
import com.nanobuntv.cartridge.netplay.Segment;
import io.netty.handler.codec.DecoderException;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.FriendlyByteBuf;

/** Encoders shared by the netplay payloads. */
final class NetCodecs {
    /** The most frames one segment may carry (a whole input log since the last savestate fits). */
    static final int MAX_FRAMES = 1 << 17;
    private static final int MAX_SEGMENTS = 64;

    private NetCodecs() {}

    /**
     * A run of inputs (netplay/InputLog): two bytes each while every one fits in 16 bits, as on every console but the
     * Pixelon 64, whose stick takes the upper 16 (D189); four otherwise.
     */
    static void writeInputs(FriendlyByteBuf buf, int[] values) {
        buf.writeVarInt(values.length);
        boolean wide = false;
        for (int v : values) wide |= (v & 0xFFFF0000) != 0;
        buf.writeBoolean(wide);
        for (int v : values) {
            if (wide) buf.writeInt(v);
            else buf.writeShort(v);
        }
    }

    static int[] readInputs(FriendlyByteBuf buf, int max) {
        int n = buf.readVarInt();
        if (n < 0 || n > max) throw new DecoderException("too many frames: " + n);
        boolean wide = buf.readBoolean();
        int[] values = new int[n];
        for (int i = 0; i < n; i++) values[i] = wide ? buf.readInt() : buf.readUnsignedShort();
        return values;
    }

    static void writeSegments(FriendlyByteBuf buf, List<Segment> segments) {
        buf.writeVarInt(segments.size());
        for (Segment s : segments) {
            buf.writeByte(s.port());
            buf.writeVarInt(s.start());
            writeInputs(buf, s.buttons());
        }
    }

    static List<Segment> readSegments(FriendlyByteBuf buf) {
        int n = buf.readVarInt();
        if (n < 0 || n > MAX_SEGMENTS) throw new DecoderException("too many segments: " + n);
        List<Segment> out = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            int port = buf.readByte();
            if (port < 0 || port >= ConsoleKind.MAX_PORTS) throw new DecoderException("bad port " + port);
            out.add(new Segment(port, buf.readVarInt(), readInputs(buf, MAX_FRAMES)));
        }
        return out;
    }

    static void writeInts(FriendlyByteBuf buf, int[] values) {
        buf.writeVarInt(values.length);
        for (int v : values) buf.writeVarInt(v);
    }

    static int[] readInts(FriendlyByteBuf buf, int max) {
        int n = buf.readVarInt();
        if (n < 0 || n > max) throw new DecoderException("too many values: " + n);
        int[] values = new int[n];
        for (int i = 0; i < n; i++) values[i] = buf.readVarInt();
        return values;
    }
}
