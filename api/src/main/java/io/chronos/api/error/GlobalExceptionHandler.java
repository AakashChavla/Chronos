package io.chronos.api.error;

import io.chronos.core.job.service.JobService.JobNotFoundException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;

@RestControllerAdvice
public final class GlobalExceptionHandler {
    @ExceptionHandler(JobNotFoundException.class)
    public ResponseEntity<ProblemDetail> missingJob(JobNotFoundException exception) {
        return problem(HttpStatus.NOT_FOUND, "JOB_NOT_FOUND", exception.getMessage());
    }

    @ExceptionHandler(ApiProblemException.class)
    public ResponseEntity<ProblemDetail> handle(ApiProblemException exception) {
        HttpStatus status =
                exception.code().equals("DUPLICATE_NAME")
                        ? HttpStatus.CONFLICT
                        : HttpStatus.UNPROCESSABLE_ENTITY;
        return problem(status, exception.code(), exception.getMessage());
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ProblemDetail> integrity(DataIntegrityViolationException exception) {
        String databaseMessage = exception.getMostSpecificCause().getMessage();
        if (databaseMessage != null
                && (databaseMessage.contains("job_definition_name_key")
                        || databaseMessage.contains("workflow_name_key"))) {
            return problem(
                    HttpStatus.CONFLICT,
                    "DUPLICATE_NAME",
                    "A resource with this name already exists.");
        }
        return problem(
                HttpStatus.UNPROCESSABLE_ENTITY,
                "INVALID_REFERENCE",
                "The request references a missing or invalid resource.");
    }

    @ExceptionHandler({
        MethodArgumentNotValidException.class,
        HandlerMethodValidationException.class
    })
    public ResponseEntity<ProblemDetail> validation(Exception exception) {
        return problem(
                HttpStatus.UNPROCESSABLE_ENTITY, "VALIDATION_422", "Request validation failed.");
    }

    private ResponseEntity<ProblemDetail> problem(HttpStatus status, String code, String detail) {
        ProblemDetail body = ProblemDetail.forStatusAndDetail(status, detail);
        body.setProperty("code", code);
        return ResponseEntity.status(status).body(body);
    }
}
