package io.chronos.common;
import org.junit.jupiter.api.Test; import static org.junit.jupiter.api.Assertions.*;
class RunStateMachineTest { private final RunStateMachine machine=new RunStateMachine();
 @Test void acceptsQueueToRunAndRunToSuccess(){assertDoesNotThrow(()->machine.require(RunStatus.QUEUED,RunStatus.RUNNING));assertDoesNotThrow(()->machine.require(RunStatus.RUNNING,RunStatus.SUCCEEDED));}
 @Test void rejectsTerminalSuccessToQueue(){assertThrows(IllegalStateException.class,()->machine.require(RunStatus.SUCCEEDED,RunStatus.QUEUED));}
 @Test void supportsRetryFromDead(){assertDoesNotThrow(()->machine.require(RunStatus.DEAD,RunStatus.QUEUED));}}
