package io.chronos.api.run;

import static org.assertj.core.api.Assertions.assertThat;

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
class JdbcRunRepositoryTest {
    @Container
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16");

    private JdbcTemplate jdbc;
    private JdbcRunRepository runs;
    private UUID jobId;

    @BeforeEach
    void setUp() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setDriverClassName("org.postgresql.Driver");
        dataSource.setUrl(POSTGRES.getJdbcUrl());
        dataSource.setUsername(POSTGRES.getUsername());
        dataSource.setPassword(POSTGRES.getPassword());
        Properties connectionProperties = new Properties();
        connectionProperties.setProperty("options", "-c TimeZone=UTC");
        dataSource.setConnectionProperties(connectionProperties);
        jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("drop table if exists job_run,job_definition cascade");
        jdbc.execute("create table job_definition (id uuid primary key)");
        jdbc.execute(
                "create table job_run (id uuid primary key default gen_random_uuid(), "
                        + "job_definition_id uuid references job_definition(id), "
                        + "status text not null default 'QUEUED', "
                        + "attempt int not null default 0, run_at timestamptz not null default now(), "
                        + "locked_by text, lease_until timestamptz, lease_token uuid, "
                        + "idempotency_key text not null default gen_random_uuid()::text, error text, "
                        + "started_at timestamptz, finished_at timestamptz, workflow_run_id uuid, "
                        + "step_key text, replay_of uuid references job_run(id))");
        runs = new JdbcRunRepository(jdbc);
        jobId = UUID.randomUUID();
        jdbc.update("insert into job_definition(id) values (?)", jobId);
    }

    @Test
    void mapsSnakeCaseFieldsAndFiltersRecentRuns() {
        UUID runId = UUID.randomUUID();
        UUID leaseToken = UUID.randomUUID();
        jdbc.update(
                "insert into job_run(id,job_definition_id,status,attempt,lease_token,step_key) "
                        + "values (?,?,?, ?,?,?)",
                runId,
                jobId,
                "DEAD",
                3,
                leaseToken,
                "transform");

        RunResponse run = runs.findRecent("DEAD").getFirst();

        assertThat(run.id()).isEqualTo(runId);
        assertThat(run.jobDefinitionId()).isEqualTo(jobId);
        assertThat(run.status()).isEqualTo("DEAD");
        assertThat(run.attempt()).isEqualTo(3);
        assertThat(run.leaseToken()).isEqualTo(leaseToken);
        assertThat(run.stepKey()).isEqualTo("transform");
        assertThat(run.runAt()).isNotNull();
    }

    @Test
    void replayCreatesQueuedRunLinkedToDeadOriginal() {
        UUID originalId = UUID.randomUUID();
        jdbc.update(
                "insert into job_run(id,job_definition_id,status) values (?,?, 'DEAD')",
                originalId,
                jobId);

        UUID replayId = runs.replayDead(originalId).orElseThrow();

        assertThat(
                        jdbc.queryForObject(
                                "select status from job_run where id=?", String.class, replayId))
                .isEqualTo("QUEUED");
        assertThat(
                        jdbc.queryForObject(
                                "select replay_of from job_run where id=?", UUID.class, replayId))
                .isEqualTo(originalId);
    }

    @Test
    void replayDoesNotCreateRunForNonDeadOrMissingOriginal() {
        UUID originalId = UUID.randomUUID();
        jdbc.update(
                "insert into job_run(id,job_definition_id,status) values (?,?, 'SUCCEEDED')",
                originalId,
                jobId);

        assertThat(runs.replayDead(originalId)).isEmpty();
        assertThat(runs.replayDead(UUID.randomUUID())).isEmpty();
        assertThat(jdbc.queryForObject("select count(*) from job_run", Integer.class)).isEqualTo(1);
    }
}
