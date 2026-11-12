package io.chronos.core.run.service;

import static org.assertj.core.api.Assertions.assertThat;

import io.chronos.common.RunStateMachine;
import io.chronos.core.config.ChronosProperties;
import io.chronos.core.run.domain.ClaimedRun;
import io.chronos.core.run.repository.JdbcRunTransitionRepository;
import java.util.List;
import java.util.Properties;
import java.util.UUID;
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
        jdbc.execute("drop table if exists job_run");
        jdbc.execute("drop table if exists job_definition");
        jdbc.execute(
                "create table job_definition (id uuid primary key, name text not null, handler_type text not null, "
                        + "payload_json jsonb not null, timeout_sec int not null, max_attempts int not null, "
                        + "concurrency_limit int not null)");
        jdbc.execute(
                "create table job_run (id uuid primary key, job_definition_id uuid not null references job_definition(id), "
                        + "status text not null default 'QUEUED', attempt int not null default 0, "
                        + "run_at timestamptz not null default now(), locked_by text, lease_until timestamptz, "
                        + "lease_token uuid, idempotency_key text not null default gen_random_uuid()::text, "
                        + "error text, started_at timestamptz, finished_at timestamptz)");
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
                                new ChronosProperties.Worker("local", true, 1000, 15000, 10),
                                new ChronosProperties.Scheduler(true, 1000)));
    }

    @Test
    void enforcesPerJobConcurrencyAndFencesStaleWorkerWrites() {
        List<ClaimedRun> firstBatch = transitions.claimBatch("worker-1");
        assertThat(firstBatch).hasSize(1);

        assertThat(transitions.claimBatch("worker-2")).isEmpty();
        ClaimedRun claimed = firstBatch.getFirst();
        assertThat(transitions.complete(claimed.id(), "worker-2", claimed.leaseToken())).isFalse();
        assertThat(transitions.complete(claimed.id(), "worker-1", UUID.randomUUID())).isFalse();
        assertThat(transitions.complete(claimed.id(), "worker-1", claimed.leaseToken())).isTrue();

        assertThat(transitions.claimBatch("worker-2")).hasSize(1);
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

    private String status(UUID runId) {
        return jdbc.queryForObject("select status from job_run where id=?", String.class, runId);
    }

    private int attempt(UUID runId) {
        return jdbc.queryForObject("select attempt from job_run where id=?", Integer.class, runId);
    }
}
