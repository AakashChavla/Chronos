package io.chronos.core.workflow.repository;

import io.chronos.core.workflow.domain.WorkflowStepDefinition;
import java.sql.Array;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcWorkflowRepository implements WorkflowRepository {
    private final JdbcTemplate jdbc;

    public JdbcWorkflowRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void insert(UUID workflowId, String name, List<WorkflowStepDefinition> steps) {
        jdbc.update("insert into workflow(id,name) values(?,?)", workflowId, name);
        for (WorkflowStepDefinition step : steps) {
            insertStep(workflowId, step);
        }
    }

    @Override
    public boolean trigger(UUID workflowId, UUID workflowRunId) {
        int inserted =
                jdbc.update(
                        "insert into workflow_run(id,workflow_id) "
                                + "select ?,id from workflow where id=?",
                        workflowRunId,
                        workflowId);
        if (inserted == 0) {
            return false;
        }
        jdbc.update(
                "insert into job_run(job_definition_id,workflow_run_id,step_key) "
                        + "select job_definition_id,?,step_key from workflow_step "
                        + "where workflow_id=? and cardinality(depends_on)=0",
                workflowRunId,
                workflowId);
        return true;
    }

    private void insertStep(UUID workflowId, WorkflowStepDefinition step) {
        jdbc.execute(
                (ConnectionCallback<Void>)
                        connection -> {
                            insertStep(connection, workflowId, step);
                            return null;
                        });
    }

    private void insertStep(Connection connection, UUID workflowId, WorkflowStepDefinition step)
            throws SQLException {
        try (PreparedStatement statement =
                connection.prepareStatement(
                        "insert into workflow_step(workflow_id,step_key,job_definition_id,depends_on) "
                                + "values(?,?,?,?)")) {
            Array dependsOn =
                    connection.createArrayOf(
                            "text",
                            step.dependsOn() == null
                                    ? new String[0]
                                    : step.dependsOn().toArray(String[]::new));
            try {
                statement.setObject(1, workflowId);
                statement.setString(2, step.stepKey());
                statement.setObject(3, step.jobDefinitionId());
                statement.setArray(4, dependsOn);
                statement.executeUpdate();
            } finally {
                dependsOn.free();
            }
        }
    }
}
