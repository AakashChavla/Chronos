package io.chronos.core.run.service;

import io.chronos.common.RunStateMachine;
import io.chronos.common.RunStatus;
import io.chronos.core.config.ChronosProperties;
import io.chronos.core.run.domain.ClaimedRun;
import io.chronos.core.run.domain.ExpiredRun;
import io.chronos.core.run.repository.RunTransitionRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RunTransitionService {
    private final RunTransitionRepository runs;
    private final RunStateMachine states;
    private final ChronosProperties.Worker workerProperties;

    public RunTransitionService(
            RunTransitionRepository runs, RunStateMachine states, ChronosProperties properties) {
        this.runs = runs;
        this.states = states;
        this.workerProperties = properties.worker();
    }

    @Transactional
    public List<ClaimedRun> claimBatch(String workerId, int availableSlots) {
        states.require(RunStatus.QUEUED, RunStatus.RUNNING);
        int requestedBatch = Math.min(availableSlots, workerProperties.batchSize());
        if (requestedBatch <= 0) {
            return List.of();
        }
        return runs.claimBatch(workerId, requestedBatch, workerProperties.leaseDurationMs());
    }

    public boolean heartbeat(UUID runId, String workerId, UUID leaseToken) {
        return runs.heartbeat(runId, workerId, leaseToken, workerProperties.leaseDurationMs());
    }

    public boolean start(UUID runId, String workerId, UUID leaseToken) {
        return runs.start(runId, workerId, leaseToken);
    }

    @Transactional
    public void recordEvent(UUID runId, String type, String workerId, int attempt, String detail) {
        runs.addRunEvent(runId, type, workerId, attempt, detail);
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
                runs.fail(
                        runId, workerId, leaseToken, nextStatus, nextAttempt, error, delaySeconds);
        if (changed) {
            runs.addErrorLog(runId, error);
        }
        return changed;
    }

    @Transactional
    public int reapExpired() {
        int changed = 0;
        for (ExpiredRun run : runs.findExpired(workerProperties.batchSize())) {
            int nextAttempt = run.attempt() + 1;
            RunStatus nextStatus =
                    nextAttempt >= run.maxAttempts() ? RunStatus.DEAD : RunStatus.QUEUED;
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
