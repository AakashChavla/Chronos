package io.chronos.worker;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.chronos.core.config.ChronosProperties;
import io.chronos.core.run.domain.ClaimedRun;
import io.chronos.core.run.domain.HeartbeatResult;
import io.chronos.core.run.service.RunTransitionService;
import io.chronos.handlers.BuiltinHandlers;
import io.chronos.handlers.JobHandler;
import java.time.Clock;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class WorkerServiceLifecycleTest {
    @Test
    void shutdownReleasesActiveClaimWithoutRecordingWorkerFailure() throws Exception {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        RunTransitionService transitions = mock(RunTransitionService.class);
        CountDownLatch handlerStarted = new CountDownLatch(1);
        CountDownLatch allowHandlerExit = new CountDownLatch(1);
        CountDownLatch handlerExited = new CountDownLatch(1);
        ClaimedRun claimed = claimedRun();
        when(jdbc.update(anyString(), anyString())).thenReturn(1);
        when(transitions.claimBatch("worker-test", 1)).thenReturn(List.of(claimed));
        when(transitions.start(claimed.id(), "worker-test", claimed.leaseToken())).thenReturn(true);
        when(transitions.release(claimed.id(), "worker-test", claimed.leaseToken()))
                .thenReturn(true);

        WorkerService worker =
                new WorkerService(
                        jdbc,
                        transitions,
                        properties(),
                        handlers(handlerStarted, allowHandlerExit, handlerExited),
                        Clock.systemUTC());
        worker.poll();
        assertThat(handlerStarted.await(5, TimeUnit.SECONDS)).isTrue();

        CountDownLatch stopped = new CountDownLatch(1);
        worker.stop(stopped::countDown);
        assertThat(stopped.await(5, TimeUnit.SECONDS)).isTrue();
        worker.poll();
        allowHandlerExit.countDown();
        assertThat(handlerExited.await(5, TimeUnit.SECONDS)).isTrue();

        verify(transitions).release(claimed.id(), "worker-test", claimed.leaseToken());
        verify(transitions, never())
                .fail(
                        any(UUID.class),
                        anyString(),
                        any(UUID.class),
                        anyInt(),
                        anyInt(),
                        anyString(),
                        anyDouble());
        verify(transitions).claimBatch("worker-test", 1);
        assertThat(worker.isRunning()).isFalse();
    }

    @Test
    void timeoutZombieKeepsCapacityAndDiscardsResultUntilHandlerExits() throws Exception {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        RunTransitionService transitions = mock(RunTransitionService.class);
        CountDownLatch handlerStarted = new CountDownLatch(1);
        CountDownLatch allowHandlerExit = new CountDownLatch(1);
        CountDownLatch handlerExited = new CountDownLatch(1);
        ClaimedRun claimed = claimedRun(1);
        when(jdbc.update(anyString(), anyString())).thenReturn(1);
        when(transitions.claimBatch("worker-test", 1)).thenReturn(List.of(claimed));
        when(transitions.start(claimed.id(), "worker-test", claimed.leaseToken())).thenReturn(true);
        when(transitions.heartbeat(claimed.id(), "worker-test", claimed.leaseToken()))
                .thenReturn(new HeartbeatResult(true, false));
        when(transitions.recordWorkerEvent(
                        claimed.id(),
                        "HANDLER_ZOMBIE",
                        "worker-test",
                        claimed.leaseToken(),
                        "Handler remained active five seconds after timeout interruption."))
                .thenReturn(true);

        WorkerService worker =
                new WorkerService(
                        jdbc,
                        transitions,
                        properties(),
                        handlers(handlerStarted, allowHandlerExit, handlerExited),
                        Clock.systemUTC());
        worker.poll();
        assertThat(handlerStarted.await(5, TimeUnit.SECONDS)).isTrue();
        verify(transitions, timeout(8000))
                .recordWorkerEvent(
                        claimed.id(),
                        "HANDLER_ZOMBIE",
                        "worker-test",
                        claimed.leaseToken(),
                        "Handler remained active five seconds after timeout interruption.");

        worker.poll();
        verify(transitions).claimBatch("worker-test", 1);
        verify(transitions, never()).complete(claimed.id(), "worker-test", claimed.leaseToken());
        verify(transitions, never())
                .fail(
                        any(UUID.class),
                        anyString(),
                        any(UUID.class),
                        anyInt(),
                        anyInt(),
                        anyString(),
                        anyDouble());

        allowHandlerExit.countDown();
        assertThat(handlerExited.await(5, TimeUnit.SECONDS)).isTrue();
        worker.stop();
    }

    private BuiltinHandlers handlers(
            CountDownLatch handlerStarted,
            CountDownLatch allowHandlerExit,
            CountDownLatch handlerExited) {
        return new BuiltinHandlers() {
            @Override
            public List<JobHandler> all() {
                return List.of(
                        new JobHandler() {
                            @Override
                            public String type() {
                                return "test-handler";
                            }

                            @Override
                            public void handle(io.chronos.common.RunContext context) {
                                handlerStarted.countDown();
                                try {
                                    while (true) {
                                        try {
                                            allowHandlerExit.await();
                                            return;
                                        } catch (InterruptedException ignored) {
                                            // Model a handler that takes longer than shutdown grace
                                            // to stop.
                                        }
                                    }
                                } finally {
                                    handlerExited.countDown();
                                }
                            }
                        });
            }
        };
    }

    private ClaimedRun claimedRun() {
        return claimedRun(30);
    }

    private ClaimedRun claimedRun(int timeoutSec) {
        return new ClaimedRun(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "idempotency-key",
                "{}",
                "test-handler",
                timeoutSec,
                3,
                0,
                1,
                30,
                1,
                0,
                UUID.randomUUID());
    }

    private ChronosProperties properties() {
        return new ChronosProperties(
                new ChronosProperties.Worker("worker-test", true, 1000, 30000, 10, 1, 100, 1000),
                new ChronosProperties.Scheduler(true, 1000));
    }
}
