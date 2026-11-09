# Chronos - agent rules

Project: distributed job scheduler and workflow engine. PostgreSQL is the queue.
Modules: common, core, handlers, api, scheduler, worker, frontend.
Stack: Java 21 (virtual threads), Spring Boot 3.4, PostgreSQL 16 + Flyway, React 18 + TypeScript.

## Commands

- Backend: `./mvnw -B verify` (includes Testcontainers integration tests)
- Frontend: `cd frontend && npm ci && npm run lint && npm run test:ci && npm run build`
- Run everything: `docker compose up --build`; smoke check: `bash scripts/demo.sh`

## Workflow

- Write an implementation plan before editing code: files to add or change, tests to write.
- One phase = one branch named `phase-N-short-name`. Small commits, conventional commit messages.
- Before finishing, run all commands above and put evidence (command + result) for every acceptance criterion in the walkthrough. List anything not done. Never claim a criterion passes without running it.

## Code rules

- One statement per line. google-java-format via Spotless. One top-level type per file. No wildcard imports.
- Package by feature; layers api -> service -> repository. SQL only in repositories.
  No business logic in controllers. DTOs are records; never return raw database rows.
- Constructor injection only. No static mutable state. Configuration through validated `@ConfigurationProperties` records (fail fast).
- Errors are RFC 7807 problem+json with a stable code. Never leak stack traces or SQL.
- Logging: SLF4J only, never `System.out`. Never log secrets, tokens or payload values that look secret. Always include runId, jobId and workerId in the MDC where known.
- Database: Flyway only, forward-only migrations. No seed data in production migrations.

## Concurrency rules (the heart of this project)

- Every change to `job_run.status` goes through `RunTransitionService`, which checks the expected current status and calls `RunStateMachine`. No other code updates status.
- Every worker write to a claimed run is guarded by the lease token. Zero rows updated means the lease was lost: stop, log WARN, never write a result.
- Use database time (`now()`) for leases and schedules, and an injected `Clock` in Java. No `System.currentTimeMillis` or `Instant.now()` in business logic.
- Delivery is at-least-once. Handlers must be idempotent. Never claim exactly-once.

## Test rules

- Every public service method and every acceptance criterion has a test.
- Unit tests: JUnit 5, Mockito, AssertJ, jqwik for properties. Integration: Testcontainers (never H2).
- No `Thread.sleep`; use Awaitility or a fake Clock. No flaky tests. Never disable or delete a test to make a build pass.

## Frontend rules

- TypeScript strict, no `any`. Feature folders. Server data only through TanStack Query hooks.
- Forms use react-hook-form + zod. Every screen has loading, empty and error states.
- Dependencies pinned with a committed lockfile; build with `npm ci`.
- Everything usable by keyboard. Tests with Testing Library and MSW.

- Do not add a dependency without listing it in the plan with the reason.
- Do not change an API path or response shape without noting it in the walkthrough.
