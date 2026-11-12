package io.chronos.api.workflow;

import io.chronos.core.workflow.domain.WorkflowStepDefinition;
import io.chronos.core.workflow.service.WorkflowService;
import io.chronos.core.workflow.service.WorkflowService.CreateWorkflowCommand;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/workflows")
public final class WorkflowController {
    private final WorkflowService workflows;

    public WorkflowController(WorkflowService workflows) {
        this.workflows = workflows;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> create(@Valid @RequestBody WorkflowRequest request) {
        UUID id =
                workflows.create(
                        new CreateWorkflowCommand(request.name(), toSteps(request.steps())));
        return Map.of("id", id);
    }

    @PostMapping("/{id}/trigger")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public Map<String, Object> trigger(@PathVariable UUID id) {
        return Map.of("workflowRunId", workflows.trigger(id));
    }

    private List<WorkflowStepDefinition> toSteps(List<WorkflowRequest.WorkflowStepRequest> steps) {
        return steps.stream()
                .map(
                        step ->
                                new WorkflowStepDefinition(
                                        step.stepKey(), step.jobDefinitionId(), step.dependsOn()))
                .toList();
    }
}
