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
