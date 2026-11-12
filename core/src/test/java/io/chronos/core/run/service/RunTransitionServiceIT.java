package io.chronos.core.run.service;

import static org.assertj.core.api.Assertions.assertThat;

import io.chronos.common.RunStateMachine;
import io.chronos.core.config.ChronosProperties;
import io.chronos.core.run.domain.ClaimedRun;
import io.chronos.core.run.repository.JdbcRunTransitionRepository;
import java.util.List;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
class RunTransitionServiceIT {
    @Container
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16");

    private JdbcTemplate jdbc;
    private RunTransitionService transitions;
    private UUID jobId;

    @BeforeEach
    void setUp() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setDriverClassName("org.postgresql.Driver");
        dataSource.setUrl(POSTGRES.getJdbcUrl());
        dataSource.setUsername(POSTGRES.getUsername());
        dataSource.setPassword(POSTGRES.getPassword());
        Properties properties = new Properties();
        properties.setProperty("options", "-c TimeZone=UTC");
        dataSource.setConnectionProperties(properties);
        jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("drop table if exists run_event");
        jdbc.execute("drop table if exists job_run");
        jdbc.execute("drop table if exists job_definition");
        jdbc.execute(
                "create table job_definition (id uuid primary key, name text not null, handler_type text not null, "
                        + "payload_json jsonb not null, timeout_sec int not null, max_attempts int not null, "
                        + "concurrency_limit int not null, enabled boolean not null default true, "
                        + "priority smallint not null default 0, backoff_base_sec float8 not null default 1, "
                        + "backoff_max_sec float8 not null default 300, backoff_jitter float8 not null default 1)");
        jdbc.execute(
                "create table job_run (id uuid primary key, job_definition_id uuid not null references job_definition(id), "
                        + "status text not null default 'QUEUED', attempt int not null default 0, "
                        + "run_at timestamptz not null default now(), locked_by text, lease_until timestamptz, "
                        + "lease_token uuid, idempotency_key text not null default gen_random_uuid()::text, "
                        + "error text, started_at timestamptz, finished_at timestamptz, "
                        + "cancel_requested boolean not null default false)");
        jdbc.execute(
                "create table run_event (id bigserial primary key, run_id uuid not null references job_run(id), "
                        + "ts timestamptz not null default now(), type text not null, worker_id text, "
                        + "attempt int not null, detail text)");
        jobId = UUID.randomUUID();
        jdbc.update(
                "insert into job_definition(id,name,handler_type,payload_json,timeout_sec,max_attempts,concurrency_limit) "
                        + "values(?,?,'sleep-and-log','{}',60,3,1)",
                jobId,
                "limited-job");
        jdbc.update(
                "insert into job_run(id,job_definition_id) values (?,?),(?,?)",
                UUID.randomUUID(),
                jobId,
                UUID.randomUUID(),
                jobId);
        transitions =
                new RunTransitionService(
                        new JdbcRunTransitionRepository(jdbc),
                        new RunStateMachine(),
                        new ChronosProperties(
                                new ChronosProperties.Worker(
                                        "local", true, 1000, 30000, 10, 20, 30000, 10000),
                                new ChronosProperties.Scheduler(true, 1000)));
    }

    @Test
    void enforcesPerJobConcurrencyAndFencesStaleWorkerWrites() {
        List<ClaimedRun> firstBatch = transitions.claimBatch("worker-1", 10);
        assertThat(firstBatch).hasSize(1);

        assertThat(transitions.claimBatch("worker-2", 10)).isEmpty();
        ClaimedRun claimed = firstBatch.getFirst();
        assertThat(transitions.complete(claimed.id(), "worker-2", claimed.leaseToken())).isFalse();
        assertThat(transitions.complete(claimed.id(), "worker-1", UUID.randomUUID())).isFalse();
        assertThat(transitions.complete(claimed.id(), "worker-1", claimed.leaseToken())).isTrue();

        assertThat(transitions.claimBatch("worker-2", 10)).hasSize(1);
    }

    @Test
    void concurrentClaimersNeverExceedPerJobConcurrencyLimit() throws Exception {
        var executor = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Future<List<ClaimedRun>> first =
                    executor.submit(
                            () -> {
                                start.await();
                                return transitions.claimBatch("worker-a", 10);
                            });
            Future<List<ClaimedRun>> second =
                    executor.submit(
                            () -> {
                                start.await();
                                return transitions.claimBatch("worker-b", 10);
                            });
            start.countDown();

            assertThat(first.get().size() + second.get().size()).isEqualTo(1);
            assertThat(
                            jdbc.queryForObject(
                                    "select count(*) from job_run where job_definition_id=? and status='RUNNING'",
                                    Integer.class,
                                    jobId))
                    .isEqualTo(1);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void expiresLeasesAndMovesExhaustedRunsToDead() {
        UUID retryingRunId = UUID.randomUUID();
        UUID exhaustedRunId = UUID.randomUUID();
        jdbc.update(
                "insert into job_run(id,job_definition_id,status,attempt,locked_by,lease_token,lease_until) "
                        + "values(?,?,'RUNNING',0,'worker-1',?,now()-interval '1 second'), "
                        + "(?,?,'RUNNING',2,'worker-1',?,now()-interval '1 second')",
                retryingRunId,
                jobId,
                UUID.randomUUID(),
                exhaustedRunId,
                jobId,
                UUID.randomUUID());

        assertThat(transitions.reapExpired()).isEqualTo(2);
        assertThat(status(retryingRunId)).isEqualTo("QUEUED");
        assertThat(attempt(retryingRunId)).isEqualTo(1);
        assertThat(status(exhaustedRunId)).isEqualTo("DEAD");
        assertThat(attempt(exhaustedRunId)).isEqualTo(3);
    }

    @Test
    void runningCancellationSignalsWorkerAndCompletesAsCancelled() {
        ClaimedRun claimed = transitions.claimBatch("worker-1", 1).getFirst();

        assertThat(transitions.cancel(claimed.id())).isTrue();
        assertThat(transitions.heartbeat(claimed.id(), "worker-1", claimed.leaseToken()))
                .satisfies(
                        heartbeat -> {
                            assertThat(heartbeat.leaseRenewed()).isTrue();
                            assertThat(heartbeat.cancellationRequested()).isTrue();
                        });
        assertThat(transitions.complete(claimed.id(), "worker-1", claimed.leaseToken())).isTrue();
        assertThat(status(claimed.id())).isEqualTo("CANCELLED");
        assertThat(
                        jdbc.queryForObject(
                                "select count(*) from run_event where run_id=? and type='CANCEL_REQUESTED'",
                                Integer.class,
                                claimed.id()))
                .isEqualTo(1);
    }

    @Test
    void queuedCancellationCompletesImmediately() {
        UUID queuedRunId = UUID.randomUUID();
        jdbc.update("insert into job_run(id,job_definition_id) values (?,?)", queuedRunId, jobId);

        assertThat(transitions.cancel(queuedRunId)).isTrue();
        assertThat(status(queuedRunId)).isEqualTo("CANCELLED");
    }

    private String status(UUID runId) {
        return jdbc.queryForObject("select status from job_run where id=?", String.class, runId);
    }

    private int attempt(UUID runId) {
        return jdbc.queryForObject("select attempt from job_run where id=?", Integer.class, runId);
    }
}
