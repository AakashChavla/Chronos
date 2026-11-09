package io.chronos.core.job.service;

import static org.assertj.core.api.Assertions.assertThat;

import io.chronos.core.job.domain.JobDefinition;
import io.chronos.core.job.repository.JdbcJobRepository;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
class JobServiceIT {
  @Container
  private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16");

  private JobService jobs;

  @BeforeEach
  void setUp() {
    DriverManagerDataSource dataSource = new DriverManagerDataSource();
    dataSource.setDriverClassName("org.postgresql.Driver");
    dataSource.setUrl(POSTGRES.getJdbcUrl());
    dataSource.setUsername(POSTGRES.getUsername());
    dataSource.setPassword(POSTGRES.getPassword());
    JdbcTemplate jdbc = new JdbcTemplate(dataSource);
    jdbc.execute("drop table if exists job_definition");
    jdbc.execute(
        "create table job_definition ("
            + "id uuid primary key, name text unique not null, handler_type text not null, "
            + "payload_json jsonb not null, schedule_cron text, timezone text not null, "
            + "schedule_next_at timestamptz, max_attempts int not null, timeout_sec int not null, "
            + "concurrency_limit int not null, enabled boolean not null, "
            + "created_at timestamptz not null default now())");
    jobs = new JobService(new JdbcJobRepository(jdbc));
  }

  @Test
  void createsListsFindsAndUpdatesJobDefinitions() {
    JobService.CreateJobCommand command = command("first", 2, 4);

    assertThat(jobs.list()).isEmpty();
    JobDefinition created = jobs.create(command);
    assertThat(created.id()).isNotNull();
    assertThat(created.payloadJson()).isEqualTo("{}");
    assertThat(created.timezone()).isEqualTo("UTC");
    assertThat(created.maxAttempts()).isEqualTo(2);
    assertThat(created.concurrencyLimit()).isEqualTo(4);
    assertThat(jobs.find(created.id())).contains(created);
    assertThat(jobs.list()).containsExactly(created);

    JobService.CreateJobCommand updated = command("renamed", 5, 8);
    assertThat(jobs.update(created.id(), updated)).isTrue();
    assertThat(jobs.find(created.id()).orElseThrow())
        .satisfies(
            job -> {
              assertThat(job.name()).isEqualTo("renamed");
              assertThat(job.maxAttempts()).isEqualTo(5);
              assertThat(job.concurrencyLimit()).isEqualTo(8);
            });
    assertThat(jobs.update(UUID.randomUUID(), updated)).isFalse();
  }

  private static JobService.CreateJobCommand command(String name, int maxAttempts, int limit) {
    return new JobService.CreateJobCommand(
        name, "sleep-and-log", null, null, null, maxAttempts, 60, limit, true);
  }
}
