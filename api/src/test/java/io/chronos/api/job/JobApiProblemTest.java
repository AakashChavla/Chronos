package io.chronos.api.job;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.chronos.api.error.GlobalExceptionHandler;
import io.chronos.core.job.service.JobService;
import io.chronos.core.job.service.JobService.CreateJobCommand;
import io.chronos.core.job.service.JobService.JobNotFoundException;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = {JobController.class, JobTriggerController.class})
@Import(GlobalExceptionHandler.class)
class JobApiProblemTest {
    @Autowired private MockMvc mvc;

    @MockBean private JobService jobs;

    @Test
    void blankJobNameReturnsStableValidationProblem() throws Exception {
        mvc.perform(
                        post("/api/jobs")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"name\":\" \",\"handlerType\":\"sleep-and-log\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("VALIDATION_422"));
    }

    @Test
    void triggeringUnknownJobReturnsStableNotFoundProblem() throws Exception {
        UUID id = UUID.randomUUID();
        when(jobs.trigger(id)).thenThrow(new JobNotFoundException(id));

        mvc.perform(post("/api/jobs/{id}/trigger", id))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("JOB_NOT_FOUND"));
    }

    @Test
    void duplicateJobNameReturnsConflictWithoutDatabaseDetails() throws Exception {
        when(jobs.create(any(CreateJobCommand.class)))
                .thenThrow(
                        new DataIntegrityViolationException(
                                "SQL hidden",
                                new RuntimeException(
                                        "duplicate key violates unique constraint "
                                                + "job_definition_name_key")));

        mvc.perform(
                        post("/api/jobs")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"name\":\"existing\",\"handlerType\":\"sleep-and-log\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DUPLICATE_NAME"))
                .andExpect(jsonPath("$.detail").value("A resource with this name already exists."));
    }
}
