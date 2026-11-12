package io.chronos.handlers;

import io.chronos.common.RunContext;

public interface JobHandler {
    String type();

    /** Handlers should poll {@link RunContext#isCancellationRequested()} during long operations. */
    void handle(RunContext context) throws Exception;
}
