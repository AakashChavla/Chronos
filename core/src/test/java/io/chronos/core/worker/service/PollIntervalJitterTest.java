package io.chronos.core.worker.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class PollIntervalJitterTest {
    private final PollIntervalJitter jitter = new PollIntervalJitter();

    @Test
    void appliesSymmetricTwentyPercentJitter() {
        assertThat(jitter.delayMillis(1000, 0.2, () -> 0)).isEqualTo(800);
        assertThat(jitter.delayMillis(1000, 0.2, () -> 0.5)).isEqualTo(1000);
        assertThat(jitter.delayMillis(1000, 0.2, () -> 0.999)).isEqualTo(1200);
    }

    @Test
    void rejectsInvalidIntervalsAndRandomValues() {
        assertThatThrownBy(() -> jitter.delayMillis(0, 0.2, () -> 0.5))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> jitter.delayMillis(1000, 0.2, () -> 1))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
