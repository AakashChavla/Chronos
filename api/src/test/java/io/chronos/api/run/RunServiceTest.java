package io.chronos.api.run;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.chronos.core.run.service.RunTransitionService;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class RunServiceTest {
    private RunRepository runs;
    private RunTransitionService transitions;
    private RunService service;

    @BeforeEach
    void setUp() {
        runs = mock(RunRepository.class);
        transitions = mock(RunTransitionService.class);
        service = new RunService(runs, transitions);
    }

    @Test
    void delegatesRecentRunSearch() {
        when(runs.findRecent("DEAD")).thenReturn(java.util.List.of());

        assertThat(service.findRecent("DEAD")).isEmpty();
        verify(runs).findRecent("DEAD");
    }

    @Test
    void retryRequiresAnAllowedTransition() {
        UUID runId = UUID.randomUUID();
        when(transitions.retry(runId)).thenReturn(true);

        service.retry(runId);

        verify(transitions).retry(runId);
    }

    @Test
    void retryRejectsAChangedOrUnknownRun() {
        UUID runId = UUID.randomUUID();
        when(transitions.retry(runId)).thenReturn(false);

        assertThatThrownBy(() -> service.retry(runId))
                .isInstanceOf(RunService.RunActionException.class)
                .hasMessage("Run cannot be retried.");
    }

    @Test
    void cancelRequiresAnAllowedTransition() {
        UUID runId = UUID.randomUUID();
        when(transitions.cancel(runId)).thenReturn(true);

        service.cancel(runId);

        verify(transitions).cancel(runId);
    }

    @Test
    void cancelRejectsAChangedOrUnknownRun() {
        UUID runId = UUID.randomUUID();
        when(transitions.cancel(runId)).thenReturn(false);

        assertThatThrownBy(() -> service.cancel(runId))
                .isInstanceOf(RunService.RunActionException.class)
                .hasMessage("Run cannot be cancelled.");
    }

    @Test
    void replayReturnsNewRunIdForDeadRun() {
        UUID runId = UUID.randomUUID();
        UUID replayId = UUID.randomUUID();
        when(runs.replayDead(runId)).thenReturn(Optional.of(replayId));

        assertThat(service.replay(runId)).isEqualTo(replayId);
    }

    @Test
    void replayRejectsNonDeadOrUnknownRun() {
        UUID runId = UUID.randomUUID();
        when(runs.replayDead(runId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.replay(runId))
                .isInstanceOf(RunService.RunActionException.class)
                .hasMessage("Only a DEAD run can be replayed.");
    }
}
