package io.chronos.api.worker;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.OffsetDateTime;

public record WorkerResponse(
        String id,
        @JsonProperty("heartbeat_at") OffsetDateTime heartbeatAt,
        @JsonProperty("current_runs") int currentRuns,
        @JsonProperty("current_running") long currentRunning) {}
