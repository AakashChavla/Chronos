# Phase 1 continuation plan: API boundaries and stable errors

## Goal
Complete the next unfinished Phase 1 slice by removing SQL and workflow validation from the legacy catch-all controller, preserving existing routes and response fields, and returning stable RFC 7807 problems for invalid input and missing or duplicate resources.

## Files to change
- `api/src/main/java/io/chronos/api/ApiController.java`: retire after its routes move to feature controllers.
- `api/src/main/java/io/chronos/api/job/JobController.java`, `JobRequest.java`: add request validation while preserving JSON compatibility.
- `api/src/main/java/io/chronos/api/{run,workflow,worker,stream}/`: add controllers and record DTOs for existing routes.
- `api/src/main/java/io/chronos/api/error/`: add stable problem codes and global exception mapping.
- `core/src/main/java/io/chronos/core/{job,run,workflow,worker}/`: add services/repositories for operations moved from the API; SQL remains in repository classes.
- `common/src/main/java/io/chronos/common/`: shared typed API errors if cross-module handling requires them.
- `api/src/test/**`, `core/src/test/**`: test validation/problem mapping and Postgres behavior for duplicate/missing job/workflow paths and DAG validation.
- `docs/implementation-plan-hardening-phase-1.md`, `CHECKLIST.md`: keep the scope and evidence current.

## Tests
- Unit tests for DAG validation: null dependencies, duplicate keys, unknown dependency, self-loop, cycle, diamond, empty graph.
- API tests for request validation, duplicate job/workflow names (409 `DUPLICATE_NAME`), unknown job/workflow trigger (404 stable code), and RFC 7807 content type/body.
- PostgreSQL Testcontainers integration tests for job create/trigger and workflow persistence/trigger.
- Run `./mvnw -B spotless:apply`, `./mvnw -B verify`, status boundary script, compose config, and frontend/build/runtime probes if deployment changes.

## Dependency changes
None planned. Existing Spring validation and test dependencies will be reused.

## Compatibility
Keep all current paths and response shapes. Error responses intentionally become stable `application/problem+json` for the documented failure cases.

## Plan update
- Add spring-boot-starter-validation to the API module so record request constraints are enforced by Spring MVC (required by Phase 1 Bean Validation acceptance).
- This slice adds job-trigger repository/service support and stable problem mapping. The remaining controller split and DAG integration move to the next continuation after the current code is verified.

