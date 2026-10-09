package com.nanobuntv.cartridge.block;

import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * A display's glass in the world (D17, D129, D131), for the renderer, the light gun and the sound: {@code origin} is
 * its top-left corner as the viewer sees it, {@code right} runs along its top edge and {@code down} down its left
 * edge, in blocks. {@code problem}: a video wall that can't show the picture says why instead (D131).
 */
public record Screen(Vec3 origin, Vec3 right, Vec3 down, Style style, @Nullable Component problem) {
    /** How the picture comes and goes: the CRT's tube (D31), or a flatscreen's backlight fade (D133). */
    public enum Style { CRT, FLAT }

    public double width() {
        return right.length();
    }

    public double height() {
        return down.length();
    }

    public Vec3 center() {
        return origin.add(right.scale(0.5)).add(down.scale(0.5));
    }

    /** Out of the glass, towards the viewer. */
    public Vec3 normal() {
        return down.cross(right).normalize();
    }

    /**
     * The game's 4:3 picture (D65, D130), centred and as large as fits: {x, y, width, height} as fractions of the
     * glass.
     */
    public double[] picture() {
        return picture(4.0 / 3.0);
    }

    /** A picture of this shape (width / height), centred and as large as fits, as {@link #picture()}. */
    public double[] picture(double aspect) {
        double w = width(), h = height();
        if (w / h > aspect) {
            double pw = h * aspect / w;
            return new double[] {(1 - pw) / 2, 0, pw, 1};
        }
        double ph = w / aspect / h;
        return new double[] {0, (1 - ph) / 2, 1, ph};
    }
}
