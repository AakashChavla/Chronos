package io.chronos.common;

import java.util.Map;
import java.util.Set;

public final class RunStateMachine {
  private static final Map<RunStatus, Set<RunStatus>> RULES =
      Map.of(
          RunStatus.QUEUED,
          Set.of(RunStatus.RUNNING, RunStatus.CANCELLED, RunStatus.SKIPPED),
          RunStatus.RUNNING,
          Set.of(
              RunStatus.QUEUED,
              RunStatus.SUCCEEDED,
              RunStatus.FAILED,
              RunStatus.DEAD,
              RunStatus.CANCELLED),
          RunStatus.FAILED,
          Set.of(RunStatus.QUEUED),
          RunStatus.DEAD,
          Set.of(RunStatus.QUEUED),
          RunStatus.SUCCEEDED,
          Set.of(),
          RunStatus.CANCELLED,
          Set.of(),
          RunStatus.SKIPPED,
          Set.of());

  public void require(RunStatus from, RunStatus to) {
    if (!RULES.getOrDefault(from, Set.of()).contains(to))
      throw new IllegalStateException("Illegal run transition: " + from + " -> " + to);
  }
}
