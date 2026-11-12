package io.chronos.core.workflow.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.chronos.core.workflow.domain.WorkflowStepDefinition;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class DagValidatorTest {
    private final DagValidator validator = new DagValidator();

    @Test
    void acceptsNullDependenciesAsRoots() {
        assertThat(validator.validate(List.of(step("extract", null)))).containsExactly("extract");
    }

    @Test
    void rejectsDuplicateStepKeysAndNamesTheDuplicate() {
        assertInvalid(List.of(step("load", null), step("load", null)), "INVALID_WORKFLOW", "load");
    }

    @Test
    void rejectsUnknownDependencyAndNamesTheDependentStep() {
        assertInvalid(List.of(step("load", List.of("transform"))), "INVALID_WORKFLOW", "load");
    }

    @Test
    void rejectsSelfLoopAsCycle() {
        assertInvalid(List.of(step("loop", List.of("loop"))), "WORKFLOW_CYCLE", "loop");
    }

    @Test
    void rejectsMultiStepCycle() {
        assertInvalid(
                List.of(step("first", List.of("second")), step("second", List.of("first"))),
                "WORKFLOW_CYCLE",
                "first");
    }

    @Test
    void returnsTopologicalOrderForDiamond() {
        assertThat(
                        validator.validate(
                                List.of(
                                        step("extract", null),
                                        step("transform", List.of("extract")),
                                        step("validate", List.of("extract")),
                                        step("load", List.of("transform", "validate")))))
                .containsExactly("extract", "transform", "validate", "load");
    }

    @Test
    void rejectsEmptyGraph() {
        assertInvalid(List.of(), "INVALID_WORKFLOW", "workflow");
    }

    private void assertInvalid(List<WorkflowStepDefinition> steps, String code, String stepKey) {
        assertThatThrownBy(() -> validator.validate(steps))
                .isInstanceOfSatisfying(
                        DagValidationException.class,
                        exception -> {
                            assertThat(exception.code()).isEqualTo(code);
                            assertThat(exception.stepKey()).isEqualTo(stepKey);
                        });
    }

    private WorkflowStepDefinition step(String key, List<String> dependencies) {
        return new WorkflowStepDefinition(key, UUID.randomUUID(), dependencies);
    }
}
