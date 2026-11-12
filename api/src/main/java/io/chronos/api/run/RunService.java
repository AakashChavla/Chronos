package io.chronos.api.run;

import io.chronos.core.run.service.RunTransitionService;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class RunService {
    private final RunRepository runs;
    private final RunTransitionService transitions;

    public RunService(RunRepository runs, RunTransitionService transitions) {
        this.runs = runs;
        this.transitions = transitions;
    }

    public List<RunResponse> findRecent(String status) {
        return runs.findRecent(status);
    }

    public void retry(UUID runId) {
        if (!transitions.retry(runId)) {
            throw new RunActionException("RUN_NOT_RETRYABLE", "Run cannot be retried.");
        }
    }

    public void cancel(UUID runId) {
        if (!transitions.cancel(runId)) {
            throw new RunActionException("RUN_NOT_CANCELLABLE", "Run cannot be cancelled.");
        }
    }

    public UUID replay(UUID originalRunId) {
        return runs.replayDead(originalRunId)
                .orElseThrow(
                        () ->
                                new RunActionException(
                                        "RUN_NOT_REPLAYABLE", "Only a DEAD run can be replayed."));
    }

    public static final class RunActionException extends RuntimeException {
        private final String code;

        public RunActionException(String code, String message) {
            super(message);
            this.code = code;
        }

        public String code() {
            return code;
        }
    }
}
