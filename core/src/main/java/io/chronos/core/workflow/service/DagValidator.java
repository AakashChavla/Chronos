package io.chronos.core.workflow.service;

import io.chronos.core.workflow.domain.WorkflowStepDefinition;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

@Component
public final class DagValidator {
    private static final Pattern STEP_KEY = Pattern.compile("[A-Za-z0-9_-]{1,100}");

    public List<String> validate(List<WorkflowStepDefinition> steps) {
        if (steps == null || steps.isEmpty()) {
            throw invalid("workflow", "Workflow must contain at least one step.");
        }

        Map<String, WorkflowStepDefinition> byKey = new HashMap<>();
        for (WorkflowStepDefinition step : steps) {
            if (step == null
                    || step.stepKey() == null
                    || !STEP_KEY.matcher(step.stepKey()).matches()) {
                throw invalid(
                        "workflow", "Step key must contain 1 to 100 letters, digits, '_' or '-'.");
            }
            if (step.jobDefinitionId() == null) {
                throw invalid(step.stepKey(), "Step must reference a job definition.");
            }
            if (byKey.putIfAbsent(step.stepKey(), step) != null) {
                throw invalid(step.stepKey(), "Duplicate step key.");
            }
        }

        Map<String, VisitState> states = new HashMap<>();
        List<String> order = new ArrayList<>();
        for (WorkflowStepDefinition step : steps) {
            visit(step.stepKey(), byKey, states, order);
        }
        return List.copyOf(order);
    }

    private void visit(
            String key,
            Map<String, WorkflowStepDefinition> steps,
            Map<String, VisitState> states,
            List<String> order) {
        VisitState state = states.get(key);
        if (state == VisitState.VISITING) {
            throw new DagValidationException("WORKFLOW_CYCLE", key, "Workflow contains a cycle.");
        }
        if (state == VisitState.VISITED) {
            return;
        }
        states.put(key, VisitState.VISITING);
        for (String dependency : dependencies(steps.get(key))) {
            if (!steps.containsKey(dependency)) {
                throw invalid(key, "Unknown dependency step: " + dependency + ".");
            }
            visit(dependency, steps, states, order);
        }
        states.put(key, VisitState.VISITED);
        order.add(key);
    }

    private List<String> dependencies(WorkflowStepDefinition step) {
        return step.dependsOn() == null ? List.of() : step.dependsOn();
    }

    private DagValidationException invalid(String stepKey, String detail) {
        return new DagValidationException("INVALID_WORKFLOW", stepKey, detail);
    }

    private enum VisitState {
        VISITING,
        VISITED
    }
}
