package io.chronos.api.run;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/runs")
public final class RunController {
    private final RunService runs;

    public RunController(RunService runs) {
        this.runs = runs;
    }

    @GetMapping
    public List<RunResponse> list(@RequestParam(name = "status", required = false) String status) {
        return runs.findRecent(status);
    }

    @PostMapping("/{id}/retry")
    public void retry(@PathVariable UUID id) {
        runs.retry(id);
    }

    @PostMapping("/{id}/cancel")
    public void cancel(@PathVariable UUID id) {
        runs.cancel(id);
    }

    @PostMapping("/{id}/replay")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public Map<String, Object> replay(@PathVariable UUID id) {
        return Map.of("runId", runs.replay(id), "replayOf", id);
    }
}
