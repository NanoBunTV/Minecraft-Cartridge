package com.nanobuntv.cartridge.block;

import net.minecraft.util.StringRepresentable;

/** How a flatscreen stands (D129): on its desk stand, or hung flat against the block behind it. */
public enum DisplayMount implements StringRepresentable {
    STAND("stand"),
    WALL("wall");

    private final String name;

    DisplayMount(String name) {
        this.name = name;
    }

    @Override
    public String getSerializedName() {
        return name;
    }
}
