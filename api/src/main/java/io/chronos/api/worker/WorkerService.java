package io.chronos.api.worker;

import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class WorkerService {
    private final WorkerRepository workers;

    public WorkerService(WorkerRepository workers) {
        this.workers = workers;
    }

    public List<WorkerResponse> list() {
        return workers.findAll();
    }
}
