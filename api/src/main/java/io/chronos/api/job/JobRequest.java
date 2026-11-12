package io.chronos.api.job;

import io.chronos.core.job.service.JobService.CreateJobCommand;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record JobRequest(
        @Size(max = 200) String name,
        @Size(max = 100) String handlerType,
        String payloadJson,
        String scheduleCron,
        String timezone,
        @Min(1) Integer maxAttempts,
        @Positive Integer timeoutSec,
        @Positive Integer concurrencyLimit,
        Boolean enabled) {
    public CreateJobCommand toCommand() {
        return new CreateJobCommand(
                name,
                handlerType,
                payloadJson,
                scheduleCron,
                timezone,
                maxAttempts,
                timeoutSec,
                concurrencyLimit,
                enabled);
    }
}
