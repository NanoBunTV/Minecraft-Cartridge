package com.nanobuntv.cartridge;

import com.nanobuntv.cartridge.platform.Services;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;

/**
 * The mod's own sounds (D108), synthesized by tools/sounds.py into assets/cartridge_mod/sounds; subtitles and file
 * lists are in sounds.json. All are played on the server, at the block, for everyone nearby.
 */
public final class CartridgeSounds {
    public static final Supplier<SoundEvent> CART_INSERT = register("console.cart_insert");
    public static final Supplier<SoundEvent> CART_EJECT = register("console.cart_eject");
    public static final Supplier<SoundEvent> POWER_ON = register("console.power_on");
    public static final Supplier<SoundEvent> POWER_OFF = register("console.power_off");
    public static final Supplier<SoundEvent> RESET = register("console.reset");
    public static final Supplier<SoundEvent> PLUG = register("console.plug");
    public static final Supplier<SoundEvent> UNPLUG = register("console.unplug");
    public static final Supplier<SoundEvent> PAD_TAKE = register("console.pad_take");
    public static final Supplier<SoundEvent> PAD_PUT_DOWN = register("console.pad_put_down");
    public static final Supplier<SoundEvent> TV_ON = register("tv.power_on"); // with the warm-up whine (D109)
    public static final Supplier<SoundEvent> TV_OFF = register("tv.power_off");
    public static final Supplier<SoundEvent> CABLE_LINK = register("tv.cable_link");

    private CartridgeSounds() {}

    public static void init() {}

    /** Plays {@code sound} at the block for everyone nearby (server side; does nothing on clients). */
    public static void play(Level level, BlockPos pos, Supplier<SoundEvent> sound, float volume) {
        if (!level.isClientSide) level.playSound(null, pos, sound.get(), SoundSource.BLOCKS, volume, 1.0F);
    }

    private static Supplier<SoundEvent> register(String name) {
        return Services.REGISTRY.register(BuiltInRegistries.SOUND_EVENT, name,
                () -> SoundEvent.createVariableRangeEvent(Cartridge.id(name)));
    }
}
