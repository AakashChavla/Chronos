package io.chronos.common;

import java.util.UUID;

public record RunContext(UUID runId, UUID jobId, String payloadJson, String idempotencyKey) {}
