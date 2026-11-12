# Delivery checklist
- [x] skeleton + compose
- [x] job CRUD and triggering
- [x] queue + virtual-thread worker
- [x] retries, dead letters, leases and reaper
- [x] workflow creation, cycle validation and dependency progression foundation
- [ ] concurrency verification and replay tests (no Testcontainers acceptance suite yet)
- [x] SSE updates and dashboard prototype (Overview, Jobs, Workflow builder, Workflow runs, Runs, Dead letters, Workers)
- [x] metrics endpoint and provisioned Grafana dashboard
- [ ] concurrency/recovery integration tests (not implemented; `./mvnw -B verify` ran 3 unit tests only)
- [x] normal-case demo: exactly 1,000 runs succeeded at attempt 0 (run with Git Bash after installing jq)
- [x] README run steps and Mermaid architecture diagram

## Phase 1 verification evidence (2026-11-09)

- `./mvnw -B verify` — passed; 3 tests, 0 failures, 0 errors. The wrapper downloads Maven 3.9.9.
- `npm ci` — passed; 73 packages audited, 0 vulnerabilities.
- `npm run lint` — unavailable; the package has no `lint` script.
- `npm run test:ci` — unavailable; the package has no `test:ci` script.
- `npm run build` — passed; Vite produced the production bundle (with upstream `use client` directive warnings).
- `docker compose config --quiet` and `docker compose -f deploy/compose/docker-compose.yml config --quiet` — both passed.
- `docker compose up -d --build` — passed after Docker Desktop recovered; PostgreSQL healthy, API and three workers started.
- `scripts/demo.sh` — passed: exactly 1,000 runs succeeded at attempt 0. The first attempt at the script exposed host prerequisites (Git Bash and jq) and an overloaded 20-client burst; the script now creates a dedicated 100-concurrency job and submits at five clients.
- Runtime probes — `/actuator/health` returned `UP`, dashboard returned HTTP 200, API returned 6 jobs and 3 workers.

## Phase 2 verification evidence (2026-11-09)

- `./mvnw -B verify` — failed in `core` because Testcontainers could not find a Docker environment from the Windows Maven process (`Could not find a valid Docker environment`). The Docker CLI and Compose work through the Docker Desktop context.
- `./mvnw -B -DskipITs verify` — passed; compiles all modules and runs the 3 existing unit tests. This explicitly skips `JobServiceIT`.
- `docker compose up -d --build` — passed with the layered API; `/actuator/health` is `UP`, `/api/jobs` returns the same snake_case field names and JSON payload wrapper, and the dashboard returns HTTP 200.
- `scripts/demo.sh` — passed again against the layered API: exactly 1,000 runs succeeded at attempt 0.
- Frontend `npm run lint` and `npm run test:ci` remain unavailable because the package scripts and their test setup have not been implemented.
- `./mvnw -B spotless:apply` — passed and formatted the Java sources with Google Java Format; `./mvnw -B -DskipITs verify` then passed with Spotless checks enabled.
- `./mvnw -B verify` — rerun after the formatting/build changes; same local Testcontainers Docker discovery error, so PostgreSQL integration test did not execute.

## Phase 3 verification evidence (2026-11-09)

- `./mvnw -B verify` — passed; 14 tests total, including 3 PostgreSQL Testcontainers integration tests for job CRUD, per-job concurrency, lease fencing, expiry, and retry/dead-letter transitions.
- `docker compose up -d --build` — passed; PostgreSQL, API/scheduler, three workers, Prometheus, Grafana, and frontend are running.
- Runtime probes — `/actuator/health` returned `UP`; `/api/jobs` returned 10 jobs; `/api/workers` returned 3 workers; frontend returned HTTP 200.
- `scripts/demo.sh` — passed against the rebuilt API: exactly 1,000 runs succeeded at attempt 0.
- `npm ci` — passed; 72 packages audited, 0 vulnerabilities. `npm run lint` — failed because no `lint` script exists; `npm run test:ci` — failed because no `test:ci` script exists. `npm run build` — passed (with upstream React Query `use client` bundler warnings).
- API paths and response shapes were not changed in this phase.
- Remaining unverified acceptance criteria include the 1,000-run concurrent submission through 3 worker containers, crash recovery during an active execution, flaky exponential retry/dead-letter progression, workflow dependency failure behavior, cycle rejection, and linked replay. Existing integration coverage is narrower than these criteria.

## Production hardening plan: Phase 1 in progress (2026-11-09)

- The earlier setup, CRUD, and transition commits did **not** complete the attached document's first two phase prompts. The full Phase 1 and Phase 2 acceptance criteria were not met. Work is now on `phase-1-foundation-hardening` and follows the document's order.
- `./mvnw -B spotless:apply` — passed with AOSP-style Google Java Format.
- `./mvnw -B verify` — passed; 14 tests, 0 failures/errors; SpotBugs + Find Security Bugs reported 0 findings in each module; Java 21 and dependency convergence checks passed.
- `./mvnw -B dependency:analyze` — command completed, but reported unused/undeclared dependency warnings; Phase 1 A8 is not satisfied.
- `bash scripts/check-status-write-boundary.sh` (Git Bash on Windows) — passed; job-run status SQL occurs in `JdbcRunTransitionRepository`, called through `RunTransitionService`.
- `docker compose config --quiet` — passed. `docker compose up -d --build` built separate runnable API, scheduler, worker, and frontend images; the first start hit the prior `app` container's occupied API port. `docker compose up -d --remove-orphans` removed the replaced container and started the split services.
- Runtime probes — API health `UP`, `/api/jobs` returned 11 jobs, `/api/workers` returned 3 workers, dashboard HTTP 200; worker has no published ports.
- `scripts/demo.sh` — passed on the split deployment: exactly 1,000 runs succeeded at attempt 0.
- The API paths, ports, and response shapes have not changed in this work slice.
- Phase 1 remains incomplete: no test-only characterization baseline for all listed scenarios was committed before this partial refactor; the REST controller still mixes endpoints and SQL; duplicate/unknown-job problem handling, DAG/configuration tests, validated properties/profiles, and package-layer enforcement remain. Phase 1 A1/A3/A4/A7 have evidence for this slice; A2/A5/A6/A8 are not fully satisfied.

## Phase 1 continuation: job API boundary slice (2026-11-12)

- Added a validated `JobRequest` record while preserving partial PUT behavior; create requires a nonblank name and handler type.
- Moved manual job triggering to `JobTriggerController`; enqueue SQL now lives in `JdbcJobRepository` behind `JobService`, and unknown or disabled jobs return a stable `JOB_NOT_FOUND` 404 problem.
- Added `GlobalExceptionHandler`: blank job name returns `VALIDATION_422`/422, duplicate job name returns `DUPLICATE_NAME`/409, invalid database references are mapped without exposing SQL, and existing routes/response fields are unchanged.
- Added `spring-boot-starter-validation` to `api` (listed in the implementation plan) and MVC/API problem tests. Added a Testcontainers job trigger integration test.
- `./mvnw -B -DskipITs -pl api -am test` — passed; 16 tests in selected reactor modules, 0 failures/errors. This did not execute the PostgreSQL IT.
- `./mvnw -B verify` — failed in `core` before API verification because Testcontainers could not discover Docker Desktop from the Windows Maven process. Setting `DOCKER_HOST=npipe:////./pipe/dockerDesktopLinuxEngine` did not resolve discovery.
- `./mvnw -B -DskipITs verify` — passed after this continuation; all modules compiled, 16 unit tests passed, Spotless passed, and SpotBugs/Find Security Bugs reported no findings.
- Compose runtime after rebuilding API: health returned `UP`; unknown job trigger returned 404; duplicate job returned 409; blank name returned 422; workers endpoint returned 3 live workers; frontend returned HTTP 200.
- `bash scripts/check-status-write-boundary.sh` (Git Bash) — passed. `docker compose up -d` restored all services. `scripts/demo.sh` — passed: exactly 1,000 runs succeeded at attempt 0.
- Remaining Phase 1 work: full Testcontainers characterization suite (including workflows/reaper/concurrency) and its pre-refactor commit evidence, split all legacy endpoints out of `ApiController`, move remaining SQL out of it, DAG validation/error tests, validated runtime properties/profiles, package-layer enforcement, and a clean dependency-analyzer report. Phase 1 is not complete.

## Phase 1 continuation: workflow service and DAG validation (2026-11-12)

- Added `DagValidator` and table-driven unit cases for null dependencies, duplicate keys, unknown dependency, self-loop, cycle, diamond, and empty graph (7 tests).
- Moved workflow create and trigger persistence into `WorkflowService` and `JdbcWorkflowRepository`; moved the HTTP operations to `WorkflowController`. Added request records and stable `WORKFLOW_CYCLE`, `INVALID_WORKFLOW`, and `WORKFLOW_NOT_FOUND` problem responses. Cycle errors include the offending `stepKey`.
- Added PostgreSQL Testcontainers workflow create/trigger integration tests. Both workflow integration tests passed during `./mvnw -B verify`.
- Compose smoke testing caught a startup failure because Spring AOP could not proxy final workflow repository/service classes. Removed `final` from the proxied classes. After rebuilding, the API started and health returned `UP`.
- `./mvnw -B -DskipITs -pl api -am test` — passed; 25 unit tests across common/core/api, 0 failures/errors (integration tests excluded).
- `./mvnw -B verify` — passed on the current code; all modules compiled, unit and Testcontainers integration tests passed, Spotless passed, SpotBugs + Find Security Bugs reported zero findings, and Java 21/dependency convergence passed. The first full run exposed a `JobServiceIT` teardown bug (dropping a referenced table); fixed the fixture and reran successfully.
- `docker compose config --quiet` — passed. `bash scripts/check-status-write-boundary.sh` (Git Bash) — passed.
- Live API probes: health `UP`; valid workflow create returned the existing `{id}` shape; trigger returned `{workflowRunId}`; cyclic DAG returned 422 `WORKFLOW_CYCLE` with `stepKey=extract`; unknown workflow trigger returned 404 `WORKFLOW_NOT_FOUND`; `/api/workers` showed 3 current workers; frontend returned HTTP 200.
- `scripts/demo.sh` — passed against the workflow-enabled API: exactly 1,000 runs succeeded at attempt 0.
- No successful API path or route changed. Error responses now use problem+json for these workflow failures.
- Phase 1 remains incomplete. `ApiController` still contains SQL for runs/replay/workers/SSE; not all endpoints have feature controllers/services; configuration properties and profiles, layer tests, full characterization coverage and pre-refactor test commit evidence are missing; The full Testcontainers suite is runnable and passed here, though the broader concurrency/reaper acceptance coverage is still missing; dependency analysis still has warnings.

## Phase 1 continuation: run API extraction and validated runtime settings (2026-11-12)

- Split `ApiController` into feature endpoints for runs, workers, and SSE, then removed the legacy controller. Run list and worker output use records and explicitly preserve existing snake_case JSON fields. SQL for list/replay/workers is in API repositories; retry and cancel delegate to `RunTransitionService`.
- Added stable 409 for non-DEAD replay and stable 422 problem responses for invalid retry/cancel requests. Successful route paths and response shapes remain the same; rejected run actions now return explicit problem responses instead of empty 200 responses.
- Added Testcontainers PostgreSQL tests for run row mapping, status filtering, DEAD replay linking, and rejection of non-DEAD/missing replay. API `JdbcRunRepositoryTest` runs under Surefire because Spring Boot repackaging prevents the Failsafe fork from loading API classes. Surefire is pinned to 3.2.5 and its JVM timezone set to UTC to avoid PostgreSQL rejecting Windows `Asia/Calcutta`.
- Added validated `ChronosProperties` for worker id, enablement, poll interval, lease duration, batch size, and scheduler polling. Worker claim SQL and scheduler/worker poll schedules consume these values. Added dev/test/prod profile files and a README settings table. Added `spring-boot-starter-validation` to `core` and Testcontainers JUnit support to `api`; reasons are in `docs/implementation-plan-hardening-phase-1-completion.md`.
- Strengthened `scripts/check-status-write-boundary.sh` to fail on `job_run` status writes outside `JdbcRunTransitionRepository`, JDBC/SQL in controllers, direct controller-to-repository imports, and core-to-API dependencies. The script is run by CI.
- `./mvnw -B verify` — passed on current code: 4 common unit tests, 17 core unit tests, 6 core Testcontainers integration tests, 19 API unit tests, and 3 API Testcontainers tests; Spotless, SpotBugs/Find Security Bugs, Java 21 and dependency convergence passed.
- `./mvnw -B dependency:analyze` — command completed successfully but still reports unused aggregate starter dependencies and undeclared transitive API usages; Phase 1 A8 remains unmet. Quartz is absent from module POMs.
- Git Bash `scripts/check-status-write-boundary.sh` — passed. `docker compose config --quiet` — passed.
- `docker compose up -d --build` — built all app/frontend images and started PostgreSQL, API, scheduler, three workers, Prometheus, Grafana, and frontend. Runtime: API health returned `UP`; `/api/workers` listed three active heartbeats; frontend returned HTTP 200; a manual job trigger returned a run with the existing snake_case field names and it reached `SUCCEEDED`.
- Git Bash `scripts/demo.sh` — passed: exactly 1,000 runs succeeded at attempt 0.
- Phase 1 still does not fully pass: the seven required pre-refactor characterization scenarios were not committed before refactoring and cannot be retroactively represented as such (A5); full characterization acceptance coverage is not present; the source boundary script is not the requested ArchUnit package-layer test (T6); `dependency:analyze` has warnings (A8); `npm run lint` and `npm run test:ci` scripts are not implemented in this phase. These remain explicit gaps before Phase 1 can be marked complete.
- Final verification after the API repository and UTC test-runner adjustments: `./mvnw -B verify` — passed in 1m48s; 46 tests total across unit and Testcontainers suites, 0 failures/errors. `npm ci` — passed (73 packages audited, 0 vulnerabilities); `npm run lint` and `npm run test:ci` — failed because scripts are absent; `npm run build` — passed with upstream TanStack Query `use client` bundler warnings.
- Final Compose rebuild from this source — passed. API health `UP`, `/api/workers` returned three current heartbeats, frontend HTTP 200, and Git Bash `scripts/demo.sh` passed with exactly 1,000 runs at attempt 0.

## Production hardening plan: Phase 2 in progress (2026-11-12)

- Branch: `phase-2-worker-correctness`; implementation plan: `docs/implementation-plan-hardening-phase-2.md`.
- Added bounded per-worker permits and batch claims; claims now select only enabled jobs, order by priority, and lock job-definition rows to serialize per-job concurrency checks. Worker starts and result writes are fenced by the lease token; a guarded STARTED run event is recorded before invoking a handler.
- Added validated worker capacity, heartbeat interval, shutdown grace, and lease settings; added per-job exponential retry base/cap/jitter and priority columns; added an atomic status-change event trigger in Flyway V3.
- Added retry-delay unit and jqwik property coverage. `./mvnw -B verify` — passed after the cancellation and concurrent-claim additions: common 4 unit tests, core 22 unit/property tests and 9 PostgreSQL Testcontainers tests, API 19 tests including 3 PostgreSQL Testcontainers tests (54 tests total); Spotless, SpotBugs, Java 21 and dependency convergence checks passed.
- `docker compose config --quiet` — passed. `docker compose up -d --build` — passed; all services started. API health and `/api/workers` returned HTTP 200 with three worker heartbeats. PostgreSQL Flyway schema history confirms V3 succeeded and `run_event` / `cancel_requested` exist.
- Git Bash `scripts/demo.sh` — passed against the rebuilt stack: all 1,000 submitted runs reached SUCCEEDED at attempt 0.
- Phase 2 is not complete: simultaneous claimers and per-job concurrency, stale-token fencing, and queued/running cancellation semantics now have Testcontainers coverage using a repository fixture. The full V3-trigger behavior is verified by Compose migration and demo, but cancellation has not been exercised through an end-to-end slow handler. Graceful shutdown/release, poll jitter, timeout-zombie fencing and its metric, NonRetryableException handling, retry-specific event detail, multi-instance advisory-lock reaper behavior, and the worker kill/pause chaos criteria remain unimplemented or unverified. Do not treat this partial Phase 2 as satisfying its full acceptance suite.
