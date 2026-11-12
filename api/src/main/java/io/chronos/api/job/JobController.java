package io.chronos.api.job;

import io.chronos.api.error.ApiProblemException;
import io.chronos.core.job.domain.JobDefinition;
import io.chronos.core.job.service.JobService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/jobs")
public final class JobController {
    private final JobService jobs;

    public JobController(JobService jobs) {
        this.jobs = jobs;
    }

    @GetMapping
    public List<JobResponse> list() {
        return jobs.list().stream().map(JobController::toResponse).toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public JobCreatedResponse create(@Valid @RequestBody JobRequest request) {
        if (request.name() == null || request.name().isBlank()) {
            throw new ApiProblemException("VALIDATION_422", "Job name is required.");
        }
        if (request.handlerType() == null || request.handlerType().isBlank()) {
            throw new ApiProblemException("VALIDATION_422", "Handler type is required.");
        }
        return new JobCreatedResponse(jobs.create(request.toCommand()).id());
    }

    @PutMapping("/{id}")
    public void update(@PathVariable("id") UUID id, @Valid @RequestBody JobRequest request) {
        jobs.update(id, request.toCommand());
    }

    private static JobResponse toResponse(JobDefinition job) {
        return new JobResponse(
                job.id(),
                job.name(),
                job.handlerType(),
                new JsonPayload("jsonb", job.payloadJson(), false),
                job.scheduleCron(),
                job.timezone(),
                job.scheduleNextAt(),
                job.maxAttempts(),
                job.timeoutSec(),
                job.concurrencyLimit(),
                job.enabled(),
                job.createdAt());
    }
}
