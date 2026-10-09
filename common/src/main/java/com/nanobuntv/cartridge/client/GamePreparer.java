package com.nanobuntv.cartridge.client;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

/**
 * Checks and readies a game file the Writer's player picked, for a {@link com.nanobuntv.cartridge.item.GameMedia} whose
 * games need more than a header check (an add-on's: Cartridge: Arcade's Arcade Boards test-boot the romset in MAME and
 * ask for its parent's or BIOS's zip, D206). Registered on the client by the media's id; runs off the render thread.
 */
@FunctionalInterface
public interface GamePreparer {
    /**
     * The result: a game ready to burn ({@code data}, its system and title), or the names of further files it needs
     * ({@code wants}, asked for one at a time and handed back in {@code extras}), or neither (an error). The message
     * is shown in the Writer either way.
     */
    record Prepared(@Nullable byte[] data, @Nullable String system, @Nullable String title, Component message,
                    List<String> wants) {
        public static Prepared ready(byte[] data, String system, String title, Component message) {
            return new Prepared(data, system, title, message, List.of());
        }

        public static Prepared needs(List<String> files, Component message) {
            return new Prepared(null, null, null, message, List.copyOf(files));
        }

        public static Prepared error(Component message) {
            return new Prepared(null, null, null, message, List.of());
        }

        public boolean ready() {
            return data != null;
        }
    }

    Prepared prepare(Path game, byte[] data, List<Path> extras) throws IOException;

    Map<String, GamePreparer> BY_MEDIA = new ConcurrentHashMap<>();

    /** Every game burned onto this media's blanks goes through {@code preparer}. */
    static void register(String mediaId, GamePreparer preparer) {
        BY_MEDIA.put(mediaId, preparer);
    }
}
