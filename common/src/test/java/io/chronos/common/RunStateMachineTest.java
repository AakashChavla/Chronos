package io.chronos.common;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class RunStateMachineTest {
  private final RunStateMachine machine = new RunStateMachine();

  @Test
  void acceptsQueueToRunAndRunToSuccess() {
    assertDoesNotThrow(() -> machine.require(RunStatus.QUEUED, RunStatus.RUNNING));
    assertDoesNotThrow(() -> machine.require(RunStatus.RUNNING, RunStatus.SUCCEEDED));
  }

  @Test
  void rejectsTerminalSuccessToQueue() {
    assertThrows(
        IllegalStateException.class, () -> machine.require(RunStatus.SUCCEEDED, RunStatus.QUEUED));
  }

  @Test
  void supportsRetryFromDead() {
    assertDoesNotThrow(() -> machine.require(RunStatus.DEAD, RunStatus.QUEUED));
  }
}
