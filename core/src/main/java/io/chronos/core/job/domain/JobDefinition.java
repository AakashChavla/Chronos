package io.chronos.core.job.domain;

import java.time.OffsetDateTime;
import java.util.UUID;

public record JobDefinition(
    UUID id,
    String name,
    String handlerType,
    String payloadJson,
    String scheduleCron,
    String timezone,
    OffsetDateTime scheduleNextAt,
    int maxAttempts,
    int timeoutSec,
    int concurrencyLimit,
    boolean enabled,
    OffsetDateTime createdAt) {}
