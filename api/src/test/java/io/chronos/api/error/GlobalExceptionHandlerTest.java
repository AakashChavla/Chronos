package io.chronos.api.error;

import static org.assertj.core.api.Assertions.assertThat;

import io.chronos.core.job.service.JobService.JobNotFoundException;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;

class GlobalExceptionHandlerTest {
    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void missingJobProducesStableNotFoundProblem() {
        var response = handler.missingJob(new JobNotFoundException(UUID.randomUUID()));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody().getProperties()).containsEntry("code", "JOB_NOT_FOUND");
    }

    @Test
    void unknownIntegrityFailureDoesNotLeakDatabaseDetails() {
        var response = handler.integrity(new DataIntegrityViolationException("secret SQL detail"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(response.getBody().getProperties()).containsEntry("code", "INVALID_REFERENCE");
        assertThat(response.getBody().getDetail()).doesNotContain("secret SQL detail");
    }
}
