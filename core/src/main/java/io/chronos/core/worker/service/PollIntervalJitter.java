package io.chronos.core.worker.service;

import java.util.function.DoubleSupplier;

public final class PollIntervalJitter {
    public long delayMillis(
            long baseIntervalMs, double jitterFraction, DoubleSupplier randomValue) {
        if (baseIntervalMs <= 0 || !Double.isFinite(jitterFraction) || jitterFraction < 0) {
            throw new IllegalArgumentException("Poll interval and jitter must be positive.");
        }
        if (jitterFraction > 1) {
            throw new IllegalArgumentException("Jitter fraction must be at most 1.");
        }
        double random = randomValue.getAsDouble();
        if (!Double.isFinite(random) || random < 0 || random >= 1) {
            throw new IllegalArgumentException("Random value must be in [0, 1).");
        }
        double factor = 1 - jitterFraction + 2 * jitterFraction * random;
        return Math.max(1, Math.round(baseIntervalMs * factor));
    }
}
