package com.nanobuntv.cartridge.item;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import java.util.UUID;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * What a burned cartridge holds (D15, D24, D44): the system it's for, the ROM's SHA-1 (the ROM itself lives in the
 * ROM store and client caches, D16), the game title, and the cart's own UUID (its battery save is keyed by it, M6).
 */
public record CartridgeContents(String system, String romSha1, String title, UUID cartId) {
    /** NES cartridges (D11). */
    public static final String NES = "nes";
    /** Famicom Disk System games (D74): burned onto the same cartridges (D79), need a BIOS. */
    public static final String FDS = "fds";
    /** Super Nintendo cartridges (D136), for the Super Pixelon. */
    public static final String SNES = "snes";
    /** Sega Genesis / Mega Drive cartridges (D151), for the Vortex Drive. */
    public static final String GENESIS = "genesis";
    /** Game Boy cartridges for the original only (D159, D170): a grey cart. */
    public static final String GAME_BOY = "gb";
    /** Game Boy cartridges that also run in colour on the Color (D170): a black cart; the Pocketron plays them too. */
    public static final String GAME_BOY_DUAL = "gb_dual";
    /** Game Boy Color-only cartridges (D170): a clear cart, for the Pocketron Color alone. */
    public static final String GAME_BOY_COLOR = "gbc";
    /** Game Boy Advance cartridges (D183), for the Pocketron Advances: a dark grey cart. */
    public static final String GAME_BOY_ADVANCE = "gba";
    /** Nintendo 64 cartridges (D189), for the Pixelon 64: a wide grey cart. Burned in big-endian (.z64) order. */
    public static final String N64 = "n64";
    /** Two Game Boy games linked as one (D177): never on a cart, only a linked game's own description. */
    public static final String GAME_BOY_LINK = "gb_link";

    public static final Codec<CartridgeContents> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.STRING.fieldOf("system").forGetter(CartridgeContents::system),
            Codec.STRING.fieldOf("rom_sha1").forGetter(CartridgeContents::romSha1),
            Codec.STRING.fieldOf("title").forGetter(CartridgeContents::title),
            UUIDUtil.CODEC.fieldOf("cart_id").forGetter(CartridgeContents::cartId)
    ).apply(i, CartridgeContents::new));

    public static final StreamCodec<ByteBuf, CartridgeContents> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, CartridgeContents::system,
            ByteBufCodecs.STRING_UTF8, CartridgeContents::romSha1,
            ByteBufCodecs.STRING_UTF8, CartridgeContents::title,
            UUIDUtil.STREAM_CODEC, CartridgeContents::cartId,
            CartridgeContents::new);
}
