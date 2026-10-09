package com.nanobuntv.cartridge.item;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import java.util.UUID;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * A handheld's own state (D158, D164), on the item: its id (its game's netplay key and resume file), whether it's
 * switched on, and a fresh random id for each start from power-on, as a console's.
 */
public record HandheldState(UUID id, boolean powered, int bootId) {
    public static final Codec<HandheldState> CODEC = RecordCodecBuilder.create(i -> i.group(
            UUIDUtil.CODEC.fieldOf("id").forGetter(HandheldState::id),
            Codec.BOOL.fieldOf("powered").forGetter(HandheldState::powered),
            Codec.INT.fieldOf("boot").forGetter(HandheldState::bootId)
    ).apply(i, HandheldState::new));

    public static final StreamCodec<ByteBuf, HandheldState> STREAM_CODEC = StreamCodec.composite(
            UUIDUtil.STREAM_CODEC, HandheldState::id,
            ByteBufCodecs.BOOL, HandheldState::powered,
            ByteBufCodecs.INT, HandheldState::bootId,
            HandheldState::new);

    public HandheldState withPower(boolean on, int boot) {
        return new HandheldState(id, on, boot);
    }
}
