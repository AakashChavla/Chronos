package io.chronos.api.job;

import io.chronos.core.job.service.JobService;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/jobs")
public final class JobTriggerController {
    private final JobService jobs;

    public JobTriggerController(JobService jobs) {
        this.jobs = jobs;
    }

    @PostMapping("/{id}/trigger")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public Map<String, Object> trigger(@PathVariable UUID id) {
        return Map.of("runId", jobs.trigger(id));
    }
}
