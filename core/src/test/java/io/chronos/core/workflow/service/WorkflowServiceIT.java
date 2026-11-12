package io.chronos.core.workflow.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.chronos.core.workflow.domain.WorkflowStepDefinition;
import io.chronos.core.workflow.repository.JdbcWorkflowRepository;
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
class WorkflowServiceIT {
    @Container
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16");

    private JdbcTemplate jdbc;
    private WorkflowService workflows;

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
        jdbc.execute(
                "drop table if exists job_run, workflow_run, workflow_step, workflow, job_definition cascade");
        jdbc.execute("create table job_definition (id uuid primary key)");
        jdbc.execute("create table workflow (id uuid primary key, name text unique not null)");
        jdbc.execute(
                "create table workflow_step (workflow_id uuid references workflow(id), step_key text not null, "
                        + "job_definition_id uuid references job_definition(id), depends_on text[] not null, "
                        + "primary key (workflow_id,step_key))");
        jdbc.execute(
                "create table workflow_run (id uuid primary key, workflow_id uuid references workflow(id), "
                        + "status text not null default 'RUNNING')");
        jdbc.execute(
                "create table job_run (id uuid primary key default gen_random_uuid(), "
                        + "job_definition_id uuid references job_definition(id), "
                        + "workflow_run_id uuid references workflow_run(id), step_key text, "
                        + "status text not null default 'QUEUED')");
        workflows = new WorkflowService(new JdbcWorkflowRepository(jdbc), new DagValidator());
    }

    @Test
    void createsDefinitionAndTriggersOnlyRootSteps() {
        UUID extractJob = UUID.randomUUID();
        UUID loadJob = UUID.randomUUID();
        jdbc.update("insert into job_definition(id) values (?), (?)", extractJob, loadJob);

        UUID workflowId =
                workflows.create(
                        new WorkflowService.CreateWorkflowCommand(
                                "extract-load",
                                List.of(
                                        step("extract", extractJob, null),
                                        step("load", loadJob, List.of("extract")))));
        UUID workflowRunId = workflows.trigger(workflowId);

        assertThat(jdbc.queryForObject("select count(*) from workflow_step", Integer.class))
                .isEqualTo(2);
        assertThat(
                        jdbc.queryForObject(
                                "select count(*) from job_run where workflow_run_id=? and step_key='extract'",
                                Integer.class,
                                workflowRunId))
                .isEqualTo(1);
        assertThat(
                        jdbc.queryForObject(
                                "select count(*) from job_run where workflow_run_id=? and step_key='load'",
                                Integer.class,
                                workflowRunId))
                .isZero();
    }

    @Test
    void missingWorkflowTriggerReturnsNotFound() {
        assertThatThrownBy(() -> workflows.trigger(UUID.randomUUID()))
                .isInstanceOf(WorkflowService.WorkflowNotFoundException.class);
    }

    private WorkflowStepDefinition step(String key, UUID jobId, List<String> dependencies) {
        return new WorkflowStepDefinition(key, jobId, dependencies);
    }
}
