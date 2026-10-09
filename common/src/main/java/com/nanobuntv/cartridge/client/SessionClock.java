package com.nanobuntv.cartridge.client;

/**
 * This client's estimate of a networked game's present frame (D12): the server's clock as of when it sent a message,
 * plus half the round trip and the time since. Small differences are eased in, so the game's pace stays even; big
 * ones (the game paused or resumed) jump.
 */
final class SessionClock {
    private static final double SNAP_FRAMES = 15;
    private static final double EASE = 0.1;

    private final double fps;
    private double base = Double.NaN;
    private long baseNanos;

    SessionClock(double fps) {
        this.fps = fps;
    }

    boolean known() {
        return !Double.isNaN(base);
    }

    double present(long now) {
        return base + (now - baseNanos) * fps / 1e9;
    }

    void sample(double serverPresent, long receivedNanos, long rttNanos, long now) {
        double estimate = serverPresent + (rttNanos / 2.0 + (now - receivedNanos)) * fps / 1e9;
        if (!known() || Math.abs(estimate - present(now)) > SNAP_FRAMES) {
            base = estimate;
            baseNanos = now;
        } else {
            base += (estimate - present(now)) * EASE;
        }
    }
}
