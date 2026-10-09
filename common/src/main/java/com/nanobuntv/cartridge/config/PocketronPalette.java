package com.nanobuntv.cartridge.config;

/** The Pocketron's screen colours for Game Boy games (D165), as SameBoy's {@code sameboy_mono_palette} names them. */
public enum PocketronPalette {
    /** The original 1989 pea-green. */
    LIME("lime"),
    /** Plain grey. */
    GREYSCALE("greyscale"),
    /** The Game Boy Pocket's. */
    OLIVE("olive"),
    /** The Game Boy Light's backlight. */
    TEAL("teal");

    public final String coreValue;

    PocketronPalette(String coreValue) {
        this.coreValue = coreValue;
    }
}
