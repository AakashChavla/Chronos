package io.chronos.core.run.domain;

import java.util.UUID;

public record ExpiredRun(UUID id, String workerId, UUID leaseToken, int attempt, int maxAttempts) {}
