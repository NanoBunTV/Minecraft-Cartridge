package com.nanobuntv.cartridge.item;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import java.util.UUID;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * A handheld's link cable to another (D176-D181), on both handhelds: the link's id (its game's netplay key and resume
 * file), which side this one is (0 for the one that asked, port 1; 1 for the other, port 2), the other handheld's id
 * and its player's name (for the GUI), and a fresh random id for each start of the linked game from power-on.
 */
public record HandheldLink(UUID linkId, int side, UUID partner, String partnerName, int bootId) {
    public static final Codec<HandheldLink> CODEC = RecordCodecBuilder.create(i -> i.group(
            UUIDUtil.CODEC.fieldOf("link").forGetter(HandheldLink::linkId),
            Codec.INT.fieldOf("side").forGetter(HandheldLink::side),
            UUIDUtil.CODEC.fieldOf("partner").forGetter(HandheldLink::partner),
            Codec.STRING.fieldOf("partner_name").forGetter(HandheldLink::partnerName),
            Codec.INT.fieldOf("boot").forGetter(HandheldLink::bootId)
    ).apply(i, HandheldLink::new));

    public static final StreamCodec<ByteBuf, HandheldLink> STREAM_CODEC = StreamCodec.composite(
            UUIDUtil.STREAM_CODEC, HandheldLink::linkId,
            ByteBufCodecs.VAR_INT, HandheldLink::side,
            UUIDUtil.STREAM_CODEC, HandheldLink::partner,
            ByteBufCodecs.stringUtf8(64), HandheldLink::partnerName,
            ByteBufCodecs.INT, HandheldLink::bootId,
            HandheldLink::new);

    public HandheldLink withBoot(int boot) {
        return new HandheldLink(linkId, side, partner, partnerName, boot);
    }
}
