package io.chronos.api.workflow;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.UUID;

public record WorkflowRequest(
        @NotBlank String name, @NotEmpty List<@Valid WorkflowStepRequest> steps) {
    public record WorkflowStepRequest(
            @NotBlank String stepKey, @NotNull UUID jobDefinitionId, List<String> dependsOn) {}
}
