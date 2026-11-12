# Phase 1 continuation plan: workflow service and DAG validation

## Goal
Move workflow definition validation, SQL, and trigger persistence from the catch-all API controller into feature-layered core services and repositories. Preserve `POST /api/workflows`, `POST /api/workflows/{id}/trigger`, and their existing success response fields.

## Files to change
- `core/src/main/java/io/chronos/core/workflow/service/`: add `DagValidator` and `WorkflowService` with step/command records and named-step validation errors.
- `core/src/main/java/io/chronos/core/workflow/repository/`: add repository interface and JDBC implementation for create and trigger operations.
- `api/src/main/java/io/chronos/api/`: add workflow controller and request records; remove workflow methods and DAG logic from `ApiController`.
- `api/src/main/java/io/chronos/api/error/GlobalExceptionHandler.java`: map workflow validation and missing-workflow exceptions to safe RFC 7807 bodies with stable codes and offending step.
- `core/src/test/java/io/chronos/core/workflow/service/`: DAG unit cases for null dependencies, duplicate keys, unknown dependency, self-loop, cycle, diamond, and empty graph.
- `core/src/test/java/io/chronos/core/workflow/service/WorkflowServiceIT.java`: PostgreSQL-backed create/trigger and missing workflow test.
- `api/src/test/java/io/chronos/api/workflow/`: HTTP problem tests for cycle rejection and unknown workflow trigger.
- `CHECKLIST.md`: record exact evidence and outstanding Phase 1 criteria.

## Tests
Run DagValidator unit tests, API MVC tests, `./mvnw -B -DskipITs verify`, attempt `./mvnw -B verify`, `docker compose config --quiet`, `scripts/check-status-write-boundary.sh`, and the Compose demo if runtime changes affect it. Testcontainers availability on this Windows host is currently unresolved and must be reported accurately.

## Dependencies
None planned; use existing Spring JDBC and test dependencies.

## Compatibility
No route or successful response shape changes. Invalid DAGs return 422 problem+json with `WORKFLOW_CYCLE` or `INVALID_WORKFLOW`, naming the offending step.
