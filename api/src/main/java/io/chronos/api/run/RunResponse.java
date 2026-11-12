package io.chronos.api.run;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.OffsetDateTime;
import java.util.UUID;

public record RunResponse(
        UUID id,
        @JsonProperty("job_definition_id") UUID jobDefinitionId,
        String status,
        int attempt,
        @JsonProperty("run_at") OffsetDateTime runAt,
        @JsonProperty("locked_by") String lockedBy,
        @JsonProperty("lease_until") OffsetDateTime leaseUntil,
        @JsonProperty("lease_token") UUID leaseToken,
        @JsonProperty("idempotency_key") String idempotencyKey,
        String error,
        @JsonProperty("started_at") OffsetDateTime startedAt,
        @JsonProperty("finished_at") OffsetDateTime finishedAt,
        @JsonProperty("workflow_run_id") UUID workflowRunId,
        @JsonProperty("step_key") String stepKey,
        @JsonProperty("replay_of") UUID replayOf) {}
