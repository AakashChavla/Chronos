package io.chronos.core.run.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.chronos.common.RunStateMachine;
import io.chronos.common.RunStatus;
import io.chronos.core.run.domain.ExpiredRun;
import io.chronos.core.run.repository.RunTransitionRepository;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class RunTransitionServiceTest {
  private RunTransitionRepository repository;
  private RunTransitionService transitions;

  @BeforeEach
  void setUp() {
    repository = mock(RunTransitionRepository.class);
    transitions = new RunTransitionService(repository, new RunStateMachine());
  }

  @Test
  void delegatesBatchClaimAfterValidatingQueuedToRunning() {
    transitions.claimBatch("worker-1");

    verify(repository).claimBatch("worker-1", 10);
  }

  @Test
  void delegatesHeartbeatAndCompletion() {
    UUID runId = UUID.randomUUID();
    UUID token = UUID.randomUUID();
    when(repository.heartbeat(runId, "worker-1", token)).thenReturn(true);
    when(repository.complete(runId, "worker-1", token)).thenReturn(true);

    assertThat(transitions.heartbeat(runId, "worker-1", token)).isTrue();
    assertThat(transitions.complete(runId, "worker-1", token)).isTrue();
    verify(repository).complete(runId, "worker-1", token);
  }

  @Test
  void recordsFailureAndChoosesRetryOrDeadState() {
    UUID runId = UUID.randomUUID();
    UUID token = UUID.randomUUID();
    when(repository.fail(runId, "worker-1", token, RunStatus.QUEUED, 1, "failed", 2.0))
        .thenReturn(true);

    assertThat(transitions.fail(runId, "worker-1", token, 0, 3, "failed", 2.0)).isTrue();

    verify(repository).addErrorLog(runId, "failed");
  }

  @Test
  void marksRunDeadWhenFailureReachesMaxAttempts() {
    UUID runId = UUID.randomUUID();
    UUID token = UUID.randomUUID();
    when(repository.fail(runId, "worker-1", token, RunStatus.DEAD, 3, "failed", 4.0))
        .thenReturn(true);

    assertThat(transitions.fail(runId, "worker-1", token, 2, 3, "failed", 4.0)).isTrue();
  }

  @Test
  void reapsExpiredRunsAndIncrementsAttempts() {
    ExpiredRun expired = new ExpiredRun(UUID.randomUUID(), "worker-1", UUID.randomUUID(), 0, 3);
    when(repository.findExpired(10)).thenReturn(List.of(expired));
    when(repository.expire(expired, RunStatus.QUEUED, 1)).thenReturn(true);

    assertThat(transitions.reapExpired()).isEqualTo(1);
  }

  @Test
  void retryAndCancelValidateStateMachineTransitions() {
    UUID runId = UUID.randomUUID();
    when(repository.retry(runId)).thenReturn(true);
    when(repository.cancel(runId)).thenReturn(true);

    assertThat(transitions.retry(runId)).isTrue();
    assertThat(transitions.cancel(runId)).isTrue();
  }

  @Test
  void skipsQueuedDependentRunsAfterValidatingTransition() {
    UUID runId = UUID.randomUUID();
    when(repository.insertFailedDependentSteps()).thenReturn(List.of(runId));
    when(repository.skip(runId)).thenReturn(true);

    assertThat(transitions.skipFailedWorkflowSteps()).isEqualTo(1);
  }
}
