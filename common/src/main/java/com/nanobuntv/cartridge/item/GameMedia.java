package com.nanobuntv.cartridge.item;

import com.nanobuntv.cartridge.rom.RomFormats;
import com.nanobuntv.cartridge.util.Compat;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.function.Supplier;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * What games are burned onto (D15, D195): a blank item the Cartridge Writer burns, the burned item it becomes (holding
 * {@link CartridgeContents}), and the kinds of game it takes. Cartridge's own is the {@link #CARTRIDGE}; an add-on
 * registers more (Cartridge: Arcade's Arcade Boards, D197) in its mod initializer, on both sides.
 *
 * @param id           unique, lower case; its name in the Writer's messages is {@code cartridge_mod.media.<id>}
 * @param systems      the systems ({@link CartridgeContents#system()}) its games may be
 * @param filePatterns the Writer's file picker filter for its games
 */
public record GameMedia(String id, Supplier<? extends Item> blank, Supplier<? extends Item> burned,
                        Predicate<String> systems, String[] filePatterns) {
    private static final List<GameMedia> ALL = new ArrayList<>();

    /** Cartridges (D15): every console's games but an add-on's. */
    public static final GameMedia CARTRIDGE = register(new GameMedia("cartridge", CartridgeItems.BLANK_CARTRIDGE,
            CartridgeItems.CARTRIDGE, RomFormats::isKnown, RomFormats.FILE_PATTERNS));

    public GameMedia {
        Objects.requireNonNull(id);
        Objects.requireNonNull(blank);
        Objects.requireNonNull(burned);
        Objects.requireNonNull(systems);
    }

    public static GameMedia register(GameMedia media) {
        synchronized (ALL) {
            if (ALL.stream().anyMatch(m -> m.id.equals(media.id))) throw new IllegalStateException(media.id + " is already registered");
            ALL.add(media);
            return media;
        }
    }

    public static List<GameMedia> values() {
        return List.copyOf(ALL);
    }

    /** The media whose blank this is. */
    public static Optional<GameMedia> ofBlank(ItemStack stack) {
        for (GameMedia media : ALL) {
            if (stack.is(media.blank.get())) return Optional.of(media);
        }
        return Optional.empty();
    }

    /** The media this burned item is (it must hold a game). */
    public static Optional<GameMedia> ofBurned(ItemStack stack) {
        if (!CartridgeComponents.CARTRIDGE.has(stack)) return Optional.empty();
        for (GameMedia media : ALL) {
            if (stack.is(media.burned.get())) return Optional.of(media);
        }
        return Optional.empty();
    }

    /** Whether this is some media's burned item: without a game it's a dummy, so creative tabs leave it out. */
    public static boolean isBurned(Item item) {
        for (GameMedia media : ALL) {
            if (item == media.burned.get()) return true;
        }
        return false;
    }

    public boolean takes(String system) {
        return systems.test(system);
    }

    /** A burned item holding {@code contents}, named after its game (D44). */
    public ItemStack create(CartridgeContents contents) {
        ItemStack stack = new ItemStack(burned.get());
        CartridgeComponents.CARTRIDGE.set(stack, contents);
        Compat.setItemName(stack, Component.literal(contents.title()));
        return stack;
    }

    public ItemStack blankStack() {
        return new ItemStack(blank.get());
    }

    /** Its name in the Writer's messages. */
    public Component displayName() {
        return Component.translatable("cartridge_mod.media." + id);
    }
}
