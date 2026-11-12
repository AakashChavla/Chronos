package io.chronos.worker;

import io.chronos.common.RunContext;
import io.chronos.core.config.ChronosProperties;
import io.chronos.core.run.domain.ClaimedRun;
import io.chronos.core.run.domain.HeartbeatResult;
import io.chronos.core.run.service.RetryPolicy;
import io.chronos.core.run.service.RunTransitionService;
import io.chronos.core.worker.service.PollIntervalJitter;
import io.chronos.handlers.BuiltinHandlers;
import io.chronos.handlers.JobHandler;
import io.chronos.handlers.NonRetryableException;
import java.time.Clock;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.SmartLifecycle;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.Trigger;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.annotation.SchedulingConfigurer;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "chronos.worker.enabled", havingValue = "true")
public class WorkerService implements SchedulingConfigurer, SmartLifecycle {
    private static final Logger LOGGER = LoggerFactory.getLogger(WorkerService.class);

    private final JdbcTemplate jdbc;
    private final RunTransitionService transitions;
    private final String worker;
    private final Map<String, JobHandler> handlers;
    private final RetryPolicy retryPolicy;
    private final Semaphore capacity;
    private final long heartbeatIntervalMs;
    private final long pollIntervalMs;
    private final long shutdownGraceMs;
    private final Clock clock;
    private final PollIntervalJitter pollJitter = new PollIntervalJitter();
    private final Map<java.util.UUID, RunExecution> activeRuns = new ConcurrentHashMap<>();
    private volatile boolean running = true;
    private final ScheduledExecutorService heartbeats =
            Executors.newSingleThreadScheduledExecutor();

    public WorkerService(
            JdbcTemplate jdbc,
            RunTransitionService transitions,
            ChronosProperties properties,
            BuiltinHandlers builtins,
            Clock clock) {
        this.jdbc = jdbc;
        this.transitions = transitions;
        this.worker = properties.worker().id();
        this.retryPolicy = new RetryPolicy();
        this.capacity = new Semaphore(properties.worker().maxConcurrency());
        this.heartbeatIntervalMs = properties.worker().heartbeatIntervalMs();
        this.pollIntervalMs = properties.worker().pollIntervalMs();
        this.shutdownGraceMs = properties.worker().shutdownGraceMs();
        this.clock = clock;
        this.handlers = new HashMap<>();
        builtins.all().forEach(h -> handlers.put(h.type(), h));
    }

    @Override
    public void configureTasks(ScheduledTaskRegistrar registrar) {
        Trigger trigger =
                context -> {
                    Instant anchor =
                            Optional.ofNullable(context.lastCompletion()).orElseGet(clock::instant);
                    long delay =
                            pollJitter.delayMillis(
                                    pollIntervalMs, 0.2, ThreadLocalRandom.current()::nextDouble);
                    return anchor.plusMillis(delay);
                };
        registrar.addTriggerTask(this::poll, trigger);
    }

    public synchronized void poll() {
        if (!running) {
            return;
        }
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
            RunExecution execution = new RunExecution(run);
            activeRuns.put(run.id(), execution);
            Thread.startVirtualThread(
                    () -> {
                        try {
                            execute(run, execution);
                        } finally {
                            capacity.release();
                            activeRuns.remove(run.id());
                            execution.finished.complete(null);
                        }
                    });
        }
    }

    private void execute(ClaimedRun run, RunExecution execution) {
        AtomicBoolean leaseLost = new AtomicBoolean();
        AtomicBoolean cancellationRequested = new AtomicBoolean();
        AtomicBoolean stopHeartbeats = new AtomicBoolean();
        ScheduledFuture<?> beat =
                heartbeats.scheduleAtFixedRate(
                        () -> {
                            if (leaseLost.get() || stopHeartbeats.get()) {
                                return;
                            }
                            HeartbeatResult result =
                                    transitions.heartbeat(run.id(), worker, run.leaseToken());
                            if (!result.leaseRenewed()) {
                                leaseLost.set(true);
                                Future<?> task = execution.handlerTask.get();
                                if (task != null) {
                                    task.cancel(true);
                                }
                                LOGGER.warn(
                                        "Worker lease lost for runId={} jobId={} workerId={}",
                                        run.id(),
                                        run.jobDefinitionId(),
                                        worker);
                            } else if (result.cancellationRequested()) {
                                cancellationRequested.set(true);
                                stopHeartbeats.set(true);
                                Future<?> task = execution.handlerTask.get();
                                if (task != null) {
                                    task.cancel(true);
                                }
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
            Future<?> task;
            try {
                task =
                        executor.submit(
                                () -> {
                                    try {
                                        handler.handle(
                                                new RunContext(
                                                        run.id(),
                                                        run.jobDefinitionId(),
                                                        run.payloadJson(),
                                                        run.idempotencyKey(),
                                                        cancellationRequested::get));
                                    } catch (Exception error) {
                                        throw new CompletionException(error);
                                    } finally {
                                        execution.handlerFinished.countDown();
                                    }
                                });
                execution.handlerTask.set(task);
                if (leaseLost.get() || cancellationRequested.get()) {
                    task.cancel(true);
                }
                task.get(run.timeoutSec(), TimeUnit.SECONDS);
            } finally {
                executor.shutdownNow();
            }
            if (leaseLost.get()) {
                return;
            }
            if (cancellationRequested.get()) {
                completeCancellation(run, execution, stopHeartbeats);
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
            if (execution.shutdownReleased.get()) {
                return;
            }
            if (error instanceof TimeoutException) {
                Future<?> task = execution.handlerTask.get();
                if (task != null) {
                    task.cancel(true);
                }
                if (!awaitHandler(run, execution.handlerFinished, 5000)) {
                    leaseLost.set(true);
                    transitions.recordWorkerEvent(
                            run.id(),
                            "HANDLER_ZOMBIE",
                            worker,
                            run.leaseToken(),
                            "Handler remained active five seconds after timeout interruption.");
                    LOGGER.error(
                            "Handler remains active after timeout for runId={} jobId={} workerId={}",
                            run.id(),
                            run.jobDefinitionId(),
                            worker);
                    awaitHandlerUntilExit(execution.handlerFinished);
                    return;
                }
            }
            if (leaseLost.get()) {
                return;
            }
            if (cancellationRequested.get()) {
                completeCancellation(run, execution, stopHeartbeats);
                return;
            }
            Throwable failure = unwrap(error);
            String message = Optional.ofNullable(failure.getMessage()).orElse(failure.toString());
            boolean changed;
            if (failure instanceof NonRetryableException) {
                changed =
                        transitions.failNonRetryable(
                                run.id(), worker, run.leaseToken(), run.attempt(), message);
            } else {
                double delay =
                        retryPolicy.delaySeconds(
                                run.attempt(),
                                run.backoffBaseSec(),
                                run.backoffMaxSec(),
                                run.backoffJitter(),
                                ThreadLocalRandom.current()::nextDouble);
                changed =
                        transitions.fail(
                                run.id(),
                                worker,
                                run.leaseToken(),
                                run.attempt(),
                                run.maxAttempts(),
                                message,
                                delay);
            }
            if (!changed) {
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

    private boolean awaitHandler(ClaimedRun run, CountDownLatch handlerFinished, long timeoutMs) {
        try {
            return handlerFinished.await(timeoutMs, TimeUnit.MILLISECONDS);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            LOGGER.warn(
                    "Worker interrupted while waiting for handler runId={} jobId={} workerId={}",
                    run.id(),
                    run.jobDefinitionId(),
                    worker);
            return handlerFinished.getCount() == 0;
        }
    }

    private void completeCancellation(
            ClaimedRun run, RunExecution execution, AtomicBoolean stopHeartbeats) {
        if (awaitHandler(run, execution.handlerFinished, heartbeatIntervalMs * 2)) {
            transitions.complete(run.id(), worker, run.leaseToken());
        } else {
            stopHeartbeats.set(true);
            LOGGER.error(
                    "Handler did not stop after cancellation signal for runId={} jobId={} workerId={}",
                    run.id(),
                    run.jobDefinitionId(),
                    worker);
        }
    }

    private void awaitHandlerUntilExit(CountDownLatch handlerFinished) {
        boolean interrupted = false;
        while (handlerFinished.getCount() > 0) {
            try {
                handlerFinished.await();
            } catch (InterruptedException ignored) {
                interrupted = true;
            }
        }
        if (interrupted) {
            Thread.currentThread().interrupt();
        }
    }

    private Throwable unwrap(Throwable error) {
        Throwable failure = error;
        while ((failure instanceof CompletionException
                        || failure instanceof java.util.concurrent.ExecutionException)
                && failure.getCause() != null) {
            failure = failure.getCause();
        }
        return failure;
    }

    @Override
    public synchronized void stop(Runnable callback) {
        running = false;
        List<RunExecution> executions = List.copyOf(activeRuns.values());
        try {
            CompletableFuture.allOf(
                            executions.stream()
                                    .map(execution -> execution.finished)
                                    .toArray(CompletableFuture[]::new))
                    .get(shutdownGraceMs, TimeUnit.MILLISECONDS);
        } catch (TimeoutException timeout) {
            activeRuns.forEach(
                    (runId, execution) -> {
                        if (transitions.release(
                                execution.run.id(), worker, execution.run.leaseToken())) {
                            execution.shutdownReleased.set(true);
                            Future<?> task = execution.handlerTask.get();
                            if (task != null) {
                                task.cancel(true);
                            }
                        }
                    });
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        } catch (ExecutionException exception) {
            LOGGER.error("Worker execution tracking failed during shutdown", exception);
        }
        heartbeats.shutdownNow();
        callback.run();
    }

    @Override
    public void stop() {
        stop(() -> {});
    }

    @Override
    public void start() {
        running = true;
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    @Override
    public boolean isAutoStartup() {
        return true;
    }

    @Override
    public int getPhase() {
        return Integer.MAX_VALUE;
    }

    private static final class RunExecution {
        private final ClaimedRun run;
        private final AtomicReference<Future<?>> handlerTask = new AtomicReference<>();
        private final CountDownLatch handlerFinished = new CountDownLatch(1);
        private final CompletableFuture<Void> finished = new CompletableFuture<>();
        private final AtomicBoolean shutdownReleased = new AtomicBoolean();

        private RunExecution(ClaimedRun run) {
            this.run = run;
        }
    }

    @Scheduled(fixedDelay = 5000)
    public void reap() {
        transitions.reapExpired();
    }
}
