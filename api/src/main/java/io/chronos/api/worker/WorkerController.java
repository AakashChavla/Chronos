package io.chronos.api.worker;

import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/workers")
public final class WorkerController {
    private final WorkerService workers;

    public WorkerController(WorkerService workers) {
        this.workers = workers;
    }

    @GetMapping
    public List<WorkerResponse> list() {
        return workers.list();
    }
}
