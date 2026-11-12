package io.chronos.core.run.service;

import java.util.function.DoubleSupplier;

public final class RetryPolicy {
    public double delaySeconds(
            int attempt,
            double baseSeconds,
            double maximumSeconds,
            double jitterSeconds,
            DoubleSupplier randomValue) {
        if (attempt < 0 || !Double.isFinite(baseSeconds) || baseSeconds < 0) {
            throw new IllegalArgumentException("Attempt and base delay must be non-negative.");
        }
        if (!Double.isFinite(maximumSeconds) || maximumSeconds < 0) {
            throw new IllegalArgumentException("Maximum delay must be non-negative.");
        }
        if (!Double.isFinite(jitterSeconds) || jitterSeconds < 0) {
            throw new IllegalArgumentException("Jitter must be non-negative.");
        }
        double random = randomValue.getAsDouble();
        if (!Double.isFinite(random) || random < 0 || random >= 1) {
            throw new IllegalArgumentException("Random value must be in [0, 1).");
        }
        double exponential = baseSeconds * Math.pow(2, attempt);
        double bounded = Math.min(maximumSeconds, exponential);
        return bounded + random * jitterSeconds;
    }
}
