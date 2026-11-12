package io.chronos.api.run;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RunRepository {
    List<RunResponse> findRecent(String status);

    Optional<UUID> replayDead(UUID originalRunId);
}
