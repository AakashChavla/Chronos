package io.chronos.api.workflow;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.chronos.api.error.GlobalExceptionHandler;
import io.chronos.core.workflow.service.DagValidationException;
import io.chronos.core.workflow.service.WorkflowService;
import io.chronos.core.workflow.service.WorkflowService.WorkflowNotFoundException;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = WorkflowController.class)
@Import(GlobalExceptionHandler.class)
class WorkflowApiProblemTest {
    @Autowired private MockMvc mvc;

    @MockBean private WorkflowService workflows;

    @Test
    void cycleProblemReturns422AndOffendingStep() throws Exception {
        UUID jobId = UUID.randomUUID();
        when(workflows.create(org.mockito.ArgumentMatchers.any()))
                .thenThrow(
                        new DagValidationException(
                                "WORKFLOW_CYCLE", "transform", "Workflow contains a cycle."));

        mvc.perform(
                        post("/api/workflows")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"name\":\"sample\",\"steps\":[{\"stepKey\":\"transform\",\"jobDefinitionId\":\""
                                                + jobId
                                                + "\",\"dependsOn\":[]}] }"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("WORKFLOW_CYCLE"))
                .andExpect(jsonPath("$.stepKey").value("transform"))
                .andExpect(
                        jsonPath("$.detail")
                                .value(org.hamcrest.Matchers.containsString("transform")));
    }

    @Test
    void unknownWorkflowTriggerReturnsStableNotFoundProblem() throws Exception {
        UUID id = UUID.randomUUID();
        when(workflows.trigger(id)).thenThrow(new WorkflowNotFoundException(id));

        mvc.perform(post("/api/workflows/{id}/trigger", id))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("WORKFLOW_NOT_FOUND"));
    }
}
