package io.chronos.core.run.service;

import io.chronos.common.RunStateMachine;
import io.chronos.common.RunStatus;
import io.chronos.core.run.domain.ClaimedRun;
import io.chronos.core.run.domain.ExpiredRun;
import io.chronos.core.run.repository.RunTransitionRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RunTransitionService {
  private static final int CLAIM_BATCH_SIZE = 10;

  private final RunTransitionRepository runs;
  private final RunStateMachine states;

  public RunTransitionService(RunTransitionRepository runs, RunStateMachine states) {
    this.runs = runs;
    this.states = states;
  }

  @Transactional
  public List<ClaimedRun> claimBatch(String workerId) {
    states.require(RunStatus.QUEUED, RunStatus.RUNNING);
    return runs.claimBatch(workerId, CLAIM_BATCH_SIZE);
  }

  public boolean heartbeat(UUID runId, String workerId, UUID leaseToken) {
    return runs.heartbeat(runId, workerId, leaseToken);
  }

  public boolean complete(UUID runId, String workerId, UUID leaseToken) {
    states.require(RunStatus.RUNNING, RunStatus.SUCCEEDED);
    return runs.complete(runId, workerId, leaseToken);
  }

  @Transactional
  public boolean fail(
      UUID runId,
      String workerId,
      UUID leaseToken,
      int attempt,
      int maxAttempts,
      String error,
      double delaySeconds) {
    int nextAttempt = attempt + 1;
    RunStatus nextStatus = nextAttempt >= maxAttempts ? RunStatus.DEAD : RunStatus.QUEUED;
    states.require(RunStatus.RUNNING, nextStatus);
    boolean changed =
        runs.fail(runId, workerId, leaseToken, nextStatus, nextAttempt, error, delaySeconds);
    if (changed) {
      runs.addErrorLog(runId, error);
    }
    return changed;
  }

  @Transactional
  public int reapExpired() {
    int changed = 0;
    for (ExpiredRun run : runs.findExpired(CLAIM_BATCH_SIZE)) {
      int nextAttempt = run.attempt() + 1;
      RunStatus nextStatus = nextAttempt >= run.maxAttempts() ? RunStatus.DEAD : RunStatus.QUEUED;
      states.require(RunStatus.RUNNING, nextStatus);
      if (runs.expire(run, nextStatus, nextAttempt)) {
        changed++;
      }
    }
    return changed;
  }

  @Transactional
  public boolean retry(UUID runId) {
    states.require(RunStatus.FAILED, RunStatus.QUEUED);
    states.require(RunStatus.DEAD, RunStatus.QUEUED);
    return runs.retry(runId);
  }

  @Transactional
  public boolean cancel(UUID runId) {
    states.require(RunStatus.QUEUED, RunStatus.CANCELLED);
    states.require(RunStatus.RUNNING, RunStatus.CANCELLED);
    return runs.cancel(runId);
  }

  @Transactional
  public int skipFailedWorkflowSteps() {
    states.require(RunStatus.QUEUED, RunStatus.SKIPPED);
    int changed = 0;
    for (UUID runId : runs.insertFailedDependentSteps()) {
      if (runs.skip(runId)) {
        changed++;
      }
    }
    return changed;
  }
}
