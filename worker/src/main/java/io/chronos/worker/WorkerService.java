package io.chronos.worker;

import io.chronos.common.RunContext;
import io.chronos.core.config.ChronosProperties;
import io.chronos.core.run.domain.ClaimedRun;
import io.chronos.core.run.service.RetryPolicy;
import io.chronos.core.run.service.RunTransitionService;
import io.chronos.handlers.BuiltinHandlers;
import io.chronos.handlers.JobHandler;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "chronos.worker.enabled", havingValue = "true")
public class WorkerService {
    private static final Logger LOGGER = LoggerFactory.getLogger(WorkerService.class);

    private final JdbcTemplate jdbc;
    private final RunTransitionService transitions;
    private final String worker;
    private final Map<String, JobHandler> handlers;
    private final RetryPolicy retryPolicy;
    private final Semaphore capacity;
    private final long heartbeatIntervalMs;
    private final ScheduledExecutorService heartbeats =
            Executors.newSingleThreadScheduledExecutor();

    public WorkerService(
            JdbcTemplate jdbc,
            RunTransitionService transitions,
            ChronosProperties properties,
            BuiltinHandlers builtins) {
        this.jdbc = jdbc;
        this.transitions = transitions;
        this.worker = properties.worker().id();
        this.retryPolicy = new RetryPolicy();
        this.capacity = new Semaphore(properties.worker().maxConcurrency());
        this.heartbeatIntervalMs = properties.worker().heartbeatIntervalMs();
        this.handlers = new HashMap<>();
        builtins.all().forEach(h -> handlers.put(h.type(), h));
    }

    @Scheduled(fixedDelayString = "${chronos.worker.poll-interval-ms:1000}")
    public void poll() {
        jdbc.update(
                "insert into worker_node(id,heartbeat_at) values (?,now()) "
                        + "on conflict(id) do update set heartbeat_at=now()",
                worker);
        int slots = capacity.availablePermits();
        if (slots == 0 || !capacity.tryAcquire(slots)) {
            return;
        }
        java.util.List<ClaimedRun> claimed;
        try {
            claimed = transitions.claimBatch(worker, slots);
        } catch (RuntimeException exception) {
            capacity.release(slots);
            throw exception;
        }
        capacity.release(slots - claimed.size());
        for (ClaimedRun run : claimed) {
            Thread.startVirtualThread(
                    () -> {
                        try {
                            execute(run);
                        } finally {
                            capacity.release();
                        }
                    });
        }
    }

    private void execute(ClaimedRun run) {
        AtomicBoolean leaseLost = new AtomicBoolean();
        AtomicReference<Future<?>> taskReference = new AtomicReference<>();
        ScheduledFuture<?> beat =
                heartbeats.scheduleAtFixedRate(
                        () -> {
                            boolean renewed =
                                    transitions.heartbeat(run.id(), worker, run.leaseToken());
                            if (!renewed) {
                                leaseLost.set(true);
                                Future<?> task = taskReference.get();
                                if (task != null) {
                                    task.cancel(true);
                                }
                                LOGGER.warn(
                                        "Worker lease lost for runId={} jobId={} workerId={}",
                                        run.id(),
                                        run.jobDefinitionId(),
                                        worker);
                            }
                        },
                        heartbeatIntervalMs,
                        heartbeatIntervalMs,
                        TimeUnit.MILLISECONDS);
        try {
            if (!transitions.start(run.id(), worker, run.leaseToken())) {
                leaseLost.set(true);
                LOGGER.warn(
                        "Worker claim expired before start for runId={} jobId={} workerId={}",
                        run.id(),
                        run.jobDefinitionId(),
                        worker);
                return;
            }
            JobHandler handler = handlers.get(run.handlerType());
            if (handler == null) {
                throw new IllegalArgumentException("unknown handler: " + run.handlerType());
            }
            ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
            try {
                Future<?> task =
                        executor.submit(
                                () -> {
                                    try {
                                        handler.handle(
                                                new RunContext(
                                                        run.id(),
                                                        run.jobDefinitionId(),
                                                        run.payloadJson(),
                                                        run.idempotencyKey()));
                                    } catch (Exception error) {
                                        throw new CompletionException(error);
                                    }
                                });
                taskReference.set(task);
                if (leaseLost.get()) {
                    task.cancel(true);
                }
                task.get(run.timeoutSec(), TimeUnit.SECONDS);
            } finally {
                executor.shutdownNow();
            }
            if (leaseLost.get()) {
                return;
            }
            if (!transitions.complete(run.id(), worker, run.leaseToken())) {
                LOGGER.warn(
                        "Worker result discarded after lease loss for runId={} jobId={} workerId={}",
                        run.id(),
                        run.jobDefinitionId(),
                        worker);
            }
        } catch (Exception error) {
            if (leaseLost.get()) {
                return;
            }
            double delay =
                    retryPolicy.delaySeconds(
                            run.attempt(),
                            run.backoffBaseSec(),
                            run.backoffMaxSec(),
                            run.backoffJitter(),
                            ThreadLocalRandom.current()::nextDouble);
            String message =
                    Optional.ofNullable(error.getCause())
                            .map(Throwable::getMessage)
                            .orElse(error.getMessage());
            if (!transitions.fail(
                    run.id(),
                    worker,
                    run.leaseToken(),
                    run.attempt(),
                    run.maxAttempts(),
                    message,
                    delay)) {
                LOGGER.warn(
                        "Worker failure discarded after lease loss for runId={} jobId={} workerId={}",
                        run.id(),
                        run.jobDefinitionId(),
                        worker);
            }
        } finally {
            beat.cancel(false);
        }
    }

    @Scheduled(fixedDelay = 5000)
    public void reap() {
        transitions.reapExpired();
    }
}
