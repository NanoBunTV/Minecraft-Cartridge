package com.nanobuntv.cartridge.client;

import com.nanobuntv.cartridge.net.RomListPayload;
import java.util.HashSet;
import java.util.Set;

/**
 * Which games the connected server has (D125), as it told this client: for the "Game missing on this server" line on
 * cartridges and the Writer's restore (D124). Render thread only.
 */
final class RomAvailability {
    private static final Set<String> STORED = new HashSet<>();
    private static boolean known;

    private RomAvailability() {}

    static void onList(RomListPayload payload) {
        if (payload.replace()) STORED.clear();
        STORED.addAll(payload.added());
        payload.removed().forEach(STORED::remove);
        known = true;
    }

    /** True only when the server has said which games it has, and this isn't one of them. */
    static boolean missing(String sha1) {
        return known && !STORED.contains(sha1);
    }

    static void clear() {
        STORED.clear();
        known = false;
    }
}
