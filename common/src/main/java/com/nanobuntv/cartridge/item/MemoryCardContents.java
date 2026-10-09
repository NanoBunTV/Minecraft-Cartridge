package com.nanobuntv.cartridge.item;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Objects;
import java.util.UUID;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * What a Memory Card holding a savestate shows (D91, D98): the card's own UUID (the savestate itself is a server file,
 * save/SaveStore), the game's title and ROM SHA-1 (D94), when it was saved, and a small screenshot of that moment:
 * {@link #THUMB_WIDTH} x {@link #THUMB_HEIGHT} pixels, RGB565, big-endian. An empty card has no component.
 */
public record MemoryCardContents(UUID cardId, String title, String romSha1, long savedAt, byte[] thumbnail) {
    /** 4:3, like the TV (D65). */
    public static final int THUMB_WIDTH = 64, THUMB_HEIGHT = 48;
    public static final int THUMB_BYTES = THUMB_WIDTH * THUMB_HEIGHT * 2;

    public static final Codec<MemoryCardContents> CODEC = RecordCodecBuilder.create(i -> i.group(
            UUIDUtil.CODEC.fieldOf("card_id").forGetter(MemoryCardContents::cardId),
            Codec.STRING.fieldOf("title").forGetter(MemoryCardContents::title),
            Codec.STRING.fieldOf("rom_sha1").forGetter(MemoryCardContents::romSha1),
            Codec.LONG.fieldOf("saved_at").forGetter(MemoryCardContents::savedAt),
            Codec.BYTE_BUFFER.xmap(MemoryCardContents::bytes, ByteBuffer::wrap).optionalFieldOf("thumbnail", new byte[0])
                    .forGetter(MemoryCardContents::thumbnail)
    ).apply(i, MemoryCardContents::new));

    public static final StreamCodec<ByteBuf, MemoryCardContents> STREAM_CODEC = StreamCodec.composite(
            UUIDUtil.STREAM_CODEC, MemoryCardContents::cardId,
            ByteBufCodecs.STRING_UTF8, MemoryCardContents::title,
            ByteBufCodecs.STRING_UTF8, MemoryCardContents::romSha1,
            ByteBufCodecs.VAR_LONG, MemoryCardContents::savedAt,
            ByteBufCodecs.byteArray(THUMB_BYTES), MemoryCardContents::thumbnail,
            MemoryCardContents::new);

    /** True if the screenshot is there and the right size. */
    public boolean hasThumbnail() {
        return thumbnail.length == THUMB_BYTES;
    }

    private static byte[] bytes(ByteBuffer buffer) {
        byte[] out = new byte[buffer.remaining()];
        buffer.duplicate().get(out);
        return out.length <= THUMB_BYTES ? out : new byte[0];
    }

    // Records compare arrays by reference; item components need equality by content.
    @Override
    public boolean equals(Object o) {
        return o instanceof MemoryCardContents c && cardId.equals(c.cardId) && title.equals(c.title)
                && romSha1.equals(c.romSha1) && savedAt == c.savedAt && Arrays.equals(thumbnail, c.thumbnail);
    }

    @Override
    public int hashCode() {
        return Objects.hash(cardId, title, romSha1, savedAt, Arrays.hashCode(thumbnail));
    }

    @Override
    public String toString() {
        return "MemoryCardContents[" + cardId + ", " + title + ", " + savedAt + "]";
    }
}
