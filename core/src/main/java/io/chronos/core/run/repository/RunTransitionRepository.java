package io.chronos.core.run.repository;

import io.chronos.common.RunStatus;
import io.chronos.core.run.domain.ClaimedRun;
import io.chronos.core.run.domain.ExpiredRun;
import java.util.List;
import java.util.UUID;

public interface RunTransitionRepository {
    List<ClaimedRun> claimBatch(String workerId, int batchSize, long leaseDurationMs);

    boolean heartbeat(UUID runId, String workerId, UUID leaseToken, long leaseDurationMs);

    boolean start(UUID runId, String workerId, UUID leaseToken);

    void addRunEvent(UUID runId, String type, String workerId, int attempt, String detail);

    boolean complete(UUID runId, String workerId, UUID leaseToken);

    boolean fail(
            UUID runId,
            String workerId,
            UUID leaseToken,
            RunStatus nextStatus,
            int nextAttempt,
            String error,
            double delaySeconds);

    List<ExpiredRun> findExpired(int batchSize);

    boolean expire(ExpiredRun run, RunStatus nextStatus, int nextAttempt);

    boolean retry(UUID runId);

    boolean cancel(UUID runId);

    List<UUID> insertFailedDependentSteps();

    boolean skip(UUID runId);

    void addErrorLog(UUID runId, String message);
}
