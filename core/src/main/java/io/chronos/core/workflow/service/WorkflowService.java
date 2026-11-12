package io.chronos.core.workflow.service;

import io.chronos.core.workflow.domain.WorkflowStepDefinition;
import io.chronos.core.workflow.repository.WorkflowRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WorkflowService {
    private final WorkflowRepository workflows;
    private final DagValidator dagValidator;

    public WorkflowService(WorkflowRepository workflows, DagValidator dagValidator) {
        this.workflows = workflows;
        this.dagValidator = dagValidator;
    }

    @Transactional
    public UUID create(CreateWorkflowCommand command) {
        if (command.name() == null || command.name().isBlank()) {
            throw new DagValidationException(
                    "INVALID_WORKFLOW", "workflow", "Workflow name is required.");
        }
        dagValidator.validate(command.steps());
        UUID id = UUID.randomUUID();
        workflows.insert(id, command.name(), command.steps());
        return id;
    }

    @Transactional
    public UUID trigger(UUID workflowId) {
        UUID runId = UUID.randomUUID();
        if (!workflows.trigger(workflowId, runId)) {
            throw new WorkflowNotFoundException(workflowId);
        }
        return runId;
    }

    public record CreateWorkflowCommand(String name, List<WorkflowStepDefinition> steps) {}

    public static final class WorkflowNotFoundException extends RuntimeException {
        public WorkflowNotFoundException(UUID workflowId) {
            super("Workflow not found: " + workflowId);
        }
    }
}
