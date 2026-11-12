package io.chronos.core.run.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import org.junit.jupiter.api.Test;

class RetryPolicyTest {
    private final RetryPolicy policy = new RetryPolicy();

    @Test
    void computesExponentialDelayWithSeededRandomValue() {
        assertThat(policy.delaySeconds(0, 2, 30, 4, () -> 0.25)).isEqualTo(3);
        assertThat(policy.delaySeconds(3, 2, 30, 4, () -> 0.5)).isEqualTo(18);
    }

    @Test
    void capsExponentialPartBeforeAddingJitter() {
        assertThat(policy.delaySeconds(20, 2, 30, 4, () -> 0.5)).isEqualTo(32);
    }

    @Test
    void rejectsInvalidParametersAndRandomValues() {
        assertThatThrownBy(() -> policy.delaySeconds(-1, 1, 2, 1, () -> 0.5))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> policy.delaySeconds(0, 1, 2, 1, () -> 1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Property
    void delayNeverGoesBelowZero(@ForAll int attempt, @ForAll double randomValue) {
        int boundedAttempt = Math.floorMod(attempt, 40);
        double random = Double.isFinite(randomValue) ? Math.abs(randomValue % 1) : 0;

        assertThat(policy.delaySeconds(boundedAttempt, 0, 300, 10, () -> random))
                .isGreaterThanOrEqualTo(0);
    }
}
