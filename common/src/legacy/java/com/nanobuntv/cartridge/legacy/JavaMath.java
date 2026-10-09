package com.nanobuntv.cartridge.legacy;

/** Java 21's {@code Math.clamp}, for the backports built with Java 17; the build points {@code Math.clamp} here. */
public final class JavaMath {
    private JavaMath() {}

    public static int clamp(long value, int min, int max) {
        return (int) Math.min(max, Math.max(value, min));
    }

    public static long clamp(long value, long min, long max) {
        return Math.min(max, Math.max(value, min));
    }

    public static double clamp(double value, double min, double max) {
        return Math.min(max, Math.max(value, min));
    }

    public static float clamp(float value, float min, float max) {
        return Math.min(max, Math.max(value, min));
    }
}
