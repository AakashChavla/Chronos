package io.chronos.core.workflow.domain;

import java.util.List;
import java.util.UUID;

public record WorkflowStepDefinition(
        String stepKey, UUID jobDefinitionId, List<String> dependsOn) {}
