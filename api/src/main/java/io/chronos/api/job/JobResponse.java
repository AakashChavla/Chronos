package io.chronos.api.job;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.OffsetDateTime;
import java.util.UUID;

public record JobResponse(
        UUID id,
        String name,
        @JsonProperty("handler_type") String handlerType,
        @JsonProperty("payload_json") JsonPayload payloadJson,
        @JsonProperty("schedule_cron") String scheduleCron,
        String timezone,
        @JsonProperty("schedule_next_at") OffsetDateTime scheduleNextAt,
        @JsonProperty("max_attempts") int maxAttempts,
        @JsonProperty("timeout_sec") int timeoutSec,
        @JsonProperty("concurrency_limit") int concurrencyLimit,
        boolean enabled,
        @JsonProperty("created_at") OffsetDateTime createdAt) {}
