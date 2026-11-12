package io.chronos.api.worker;

import java.util.List;

public interface WorkerRepository {
    List<WorkerResponse> findAll();
}
