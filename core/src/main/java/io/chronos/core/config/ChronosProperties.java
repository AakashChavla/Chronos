package io.chronos.core.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("chronos")
public record ChronosProperties(
        @Valid @DefaultValue Worker worker, @Valid @DefaultValue Scheduler scheduler) {
    public record Worker(
            @NotBlank @DefaultValue("local") String id,
            @DefaultValue("true") boolean enabled,
            @Min(100) @Max(60000) @DefaultValue("1000") long pollIntervalMs,
            @Min(1000) @Max(300000) @DefaultValue("30000") long leaseDurationMs,
            @Min(1) @Max(1000) @DefaultValue("10") int batchSize,
            @Min(1) @Max(1000) @DefaultValue("20") int maxConcurrency,
            @Min(100) @Max(300000) @DefaultValue("30000") long shutdownGraceMs,
            @Min(100) @Max(60000) @DefaultValue("10000") long heartbeatIntervalMs) {}

    public record Scheduler(
            @DefaultValue("true") boolean enabled,
            @Min(100) @Max(60000) @DefaultValue("1000") long pollIntervalMs) {}
}
