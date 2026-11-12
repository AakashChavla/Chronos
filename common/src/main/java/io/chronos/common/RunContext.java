package io.chronos.common;

import java.util.UUID;
import java.util.function.BooleanSupplier;

public record RunContext(
        UUID runId,
        UUID jobId,
        String payloadJson,
        String idempotencyKey,
        BooleanSupplier cancellationRequested) {
    public RunContext(UUID runId, UUID jobId, String payloadJson, String idempotencyKey) {
        this(runId, jobId, payloadJson, idempotencyKey, () -> false);
    }

    public boolean isCancellationRequested() {
        return cancellationRequested.getAsBoolean();
    }
}
