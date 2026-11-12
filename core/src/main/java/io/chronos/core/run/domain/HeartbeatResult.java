package io.chronos.core.run.domain;

public record HeartbeatResult(boolean leaseRenewed, boolean cancellationRequested) {}
