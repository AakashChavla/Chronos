# Hardening Phase 1 implementation plan

## Scope

The earlier repository setup, job CRUD, and run-transition commits do not complete Phase 1 from the supplied production-hardening plan. This branch addresses the remaining Phase 1 foundation work, beginning with characterization tests before structural refactoring. Existing API paths and response shapes will be preserved unless a compatibility issue is found; any required change will be recorded in the walkthrough.

## Work order

1. **Characterize current behavior before refactoring.** Add PostgreSQL Testcontainers integration coverage for job create/trigger/execute, retry to DEAD, workflow dependency ordering and cycle rejection, expired-lease reaping, and per-job concurrency. Commit this test-only baseline before structural changes.
2. **Build and code-quality foundation.** Inspect the current Maven wrapper and parent POM; add the missing Phase 1 Maven Enforcer and SpotBugs/Find Security Bugs gates, remove the unused Quartz dependency if confirmed unused, and format/check Java. Avoid unplanned dependencies; any new build plugin is explained in this plan.
3. **Separate runnable applications.** Add runnable scheduler and worker entry points/configurations; ensure the API alone owns Flyway and HTTP/metrics. Update the shared parametrized Dockerfile and Compose module configuration while retaining the existing external ports and API contract.
4. **Complete API layering and error handling.** Split endpoints by feature into controllers, move SQL into repositories and behavior into services, use validated record DTOs, and add stable RFC 7807 errors for validation, missing jobs, duplicate names, and invalid workflows.
5. **Enforce run state and validated runtime configuration.** Route all status updates through `RunTransitionService` with expected-status checks and `RunStateMachine`; document status semantics; add validated properties for polling, lease, batch size and worker identity; add dev/test/prod profiles.
6. **Document and gate.** Add a Phase 1 walkthrough/checklist evidence, README project structure, and tests for the state machine, DAG validation, problem handling, configuration, status-write boundary, and package layers.

## Files expected to change

- `core/src/test/**`: Testcontainers behavioral characterization and focused DAG/configuration tests.
- `pom.xml`, module POMs: Enforcer and SpotBugs/Find Security Bugs configuration; removal of unused Quartz if confirmed.
- `api/src/main/java/**`, `scheduler/src/main/java/**`, `worker/src/main/java/**`: app entry points and feature controllers/configuration.
- `core/src/main/java/**`, `common/src/main/java/**`: repository/service boundaries, validation/configuration and shared error/state types.
- `Dockerfile`, `docker-compose.yml`, `.github/workflows/ci.yml`: distinct app builds, runtime roles, and full verification gate.
- `docs/adr/0002-run-status-model.md`, `README.md`, `CHECKLIST.md`, this plan: status model, architecture notes, and evidence.

## Test plan

- Baseline Testcontainers tests are committed before the application/code-structure refactor.
- Unit tests cover legal/illegal state transitions, DAG edge cases, exception-to-problem mappings, and validated configuration.
- Integration tests cover API job errors and queue/lease/concurrency behavior against PostgreSQL 16.
- A grep/invariant test enforces that only `RunTransitionService` writes `job_run.status`; architecture tests enforce feature layering.
- Run `./mvnw -B verify`, `./mvnw -B dependency:analyze`, frontend CI commands where implemented, `docker compose up -d --build`, runtime probes, and `scripts/demo.sh`; record exact results and remaining unverified acceptance criteria.

## Dependency changes

- Planned: Maven Enforcer plugin 3.5.0 and SpotBugs plugin 4.8.6.6 with Find Security Bugs 1.13.0, because Phase 1 explicitly requires Java/dependency convergence and static security analysis.
- Planned test dependencies for the worker module: `spring-boot-starter-test` and Testcontainers JUnit/PostgreSQL, because the required characterization test must execute the real worker against Postgres. No runtime dependency is planned.
