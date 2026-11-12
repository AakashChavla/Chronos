package io.chronos.core.workflow.repository;

import io.chronos.core.workflow.domain.WorkflowStepDefinition;
import java.util.List;
import java.util.UUID;

public interface WorkflowRepository {
    void insert(UUID workflowId, String name, List<WorkflowStepDefinition> steps);

    boolean trigger(UUID workflowId, UUID workflowRunId);
}
