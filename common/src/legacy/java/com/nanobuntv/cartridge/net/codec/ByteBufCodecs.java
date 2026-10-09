package com.nanobuntv.cartridge.net.codec;

import io.netty.buffer.ByteBuf;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;

/**
 * Minecraft's {@code ByteBufCodecs} (1.20.5), for the versions before it: the codecs the mod uses, over
 * {@link FriendlyByteBuf}'s own reads and writes. Also the stream codecs vanilla types gained in 1.20.5
 * ({@code BlockPos.STREAM_CODEC} and the like), which the build points here.
 */
public final class ByteBufCodecs {
    public static final StreamCodec<ByteBuf, Boolean> BOOL = StreamCodec.of(ByteBuf::writeBoolean, ByteBuf::readBoolean);
    public static final StreamCodec<ByteBuf, Integer> INT = StreamCodec.of(ByteBuf::writeInt, ByteBuf::readInt);
    public static final StreamCodec<ByteBuf, Double> DOUBLE = StreamCodec.of(ByteBuf::writeDouble, ByteBuf::readDouble);
    public static final StreamCodec<ByteBuf, Integer> VAR_INT = StreamCodec.of(
            (buf, v) -> friendly(buf).writeVarInt(v), buf -> friendly(buf).readVarInt());
    public static final StreamCodec<ByteBuf, Long> VAR_LONG = StreamCodec.of(
            (buf, v) -> friendly(buf).writeVarLong(v), buf -> friendly(buf).readVarLong());
    public static final StreamCodec<ByteBuf, String> STRING_UTF8 = stringUtf8(32767);

    public static final StreamCodec<ByteBuf, BlockPos> BLOCK_POS = StreamCodec.of(
            (buf, v) -> friendly(buf).writeBlockPos(v), buf -> friendly(buf).readBlockPos());
    public static final StreamCodec<ByteBuf, UUID> UUID = StreamCodec.of(
            (buf, v) -> friendly(buf).writeUUID(v), buf -> friendly(buf).readUUID());
    public static final StreamCodec<ByteBuf, GlobalPos> GLOBAL_POS = StreamCodec.of(
            (buf, v) -> friendly(buf).writeGlobalPos(v), buf -> friendly(buf).readGlobalPos());
    public static final StreamCodec<ByteBuf, Component> COMPONENT = StreamCodec.of(
            (buf, v) -> friendly(buf).writeComponent(v), buf -> friendly(buf).readComponent());

    private ByteBufCodecs() {}

    public static StreamCodec<ByteBuf, String> stringUtf8(int max) {
        return StreamCodec.of((buf, v) -> friendly(buf).writeUtf(v, max), buf -> friendly(buf).readUtf(max));
    }

    public static StreamCodec<ByteBuf, byte[]> byteArray(int max) {
        return StreamCodec.of((buf, v) -> friendly(buf).writeByteArray(v), buf -> friendly(buf).readByteArray(max));
    }

    public static <B extends ByteBuf, V> StreamCodec.CodecOperation<B, V, List<V>> list() {
        return codec -> StreamCodec.of((buf, list) -> {
            friendly(buf).writeVarInt(list.size());
            for (V v : list) codec.encode(buf, v);
        }, buf -> {
            int n = friendly(buf).readVarInt();
            List<V> list = new ArrayList<>(Math.min(n, 65536));
            for (int i = 0; i < n; i++) list.add(codec.decode(buf));
            return list;
        });
    }

    private static FriendlyByteBuf friendly(ByteBuf buf) {
        return buf instanceof FriendlyByteBuf f ? f : new FriendlyByteBuf(buf);
    }
}
