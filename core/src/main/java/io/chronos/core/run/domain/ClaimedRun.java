package io.chronos.core.run.domain;

import java.util.UUID;

public record ClaimedRun(
        UUID id,
        UUID jobDefinitionId,
        String idempotencyKey,
        String payloadJson,
        String handlerType,
        int timeoutSec,
        int maxAttempts,
        int attempt,
        UUID leaseToken) {}
