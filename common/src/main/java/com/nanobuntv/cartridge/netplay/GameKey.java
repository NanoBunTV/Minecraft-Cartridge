package com.nanobuntv.cartridge.netplay;

import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.DecoderException;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.codec.StreamCodec;
import org.jetbrains.annotations.Nullable;

/**
 * Which game a netplay message is about (D12, D158): a console block's, by its position (in the player's dimension), or
 * a handheld's, by the handheld item's own id, wherever its holder carries it.
 */
public record GameKey(@Nullable BlockPos console, @Nullable UUID handheld) {
    public GameKey {
        if ((console == null) == (handheld == null)) throw new IllegalArgumentException("a console or a handheld");
        if (console != null) console = console.immutable();
    }

    public static GameKey console(BlockPos pos) {
        return new GameKey(pos, null);
    }

    public static GameKey handheld(UUID id) {
        return new GameKey(null, id);
    }

    public boolean isHandheld() {
        return handheld != null;
    }

    /** For log lines. */
    public String describe() {
        return console != null ? console.toShortString() : "handheld " + handheld;
    }

    public static final StreamCodec<ByteBuf, GameKey> STREAM_CODEC = StreamCodec.of(
            (buf, key) -> {
                buf.writeBoolean(key.handheld != null);
                if (key.handheld != null) UUIDUtil.STREAM_CODEC.encode(buf, key.handheld);
                else BlockPos.STREAM_CODEC.encode(buf, key.console);
            },
            buf -> {
                try {
                    return buf.readBoolean() ? handheld(UUIDUtil.STREAM_CODEC.decode(buf)) : console(BlockPos.STREAM_CODEC.decode(buf));
                } catch (IllegalArgumentException e) {
                    throw new DecoderException(e);
                }
            });
}
