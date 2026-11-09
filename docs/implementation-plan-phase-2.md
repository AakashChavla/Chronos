# Phase 2: layered job API

## Scope

Move job definition persistence and CRUD behavior out of `ApiController` without changing the existing REST paths or JSON response shape. This phase does not extract run and workflow SQL.

## Files to add or change

- Add a `JobDefinition` domain record, `JobRepository` port, `JdbcJobRepository` implementation, and `JobService` under `core/src/main/java/io/chronos/core/job/`.
- Add record request/response DTOs for job CRUD under `api/src/main/java/io/chronos/api/job/` and route the existing `/api/jobs` GET, POST, and PUT methods through `JobService`.
- Add core test dependencies (`spring-boot-starter-test`, Testcontainers JUnit/PostgreSQL) so the repository can be integration-tested against PostgreSQL; no production dependency is added.
- Import the Testcontainers BOM to pin its test-only modules consistently; add Maven Failsafe so `*IT` tests run during `verify`.
- Add the requested Spotless/Google Java Format, JaCoCo, SpotBugs, and PIT Maven plugins in the parent POM for consistent formatting, coverage, static analysis, and mutation testing.
- Apply Google Java Format and replace wildcard imports and `System.out` logging in the Java files included in this phase.
- Replace scheduler wall-clock reads with the database timestamp already used to select due schedules.
- Bind Spotless checks to `verify` so formatting is enforced by local and CI builds.
- Add repository integration tests for create/list/update against a disposable PostgreSQL container and document exact verification results.
- Replace the brief `AGENTS.md` with the full project rules supplied by the user and update GitHub Actions to run Maven `verify` plus the available frontend CI commands.

## Verification

- Run `./mvnw -B verify`; confirm unit tests and PostgreSQL integration tests execute.
- Run frontend lint/test/build commands and report any scripts that do not exist.
- Recheck current Compose health and rerun `scripts/demo.sh` because the API controller wiring changes.
- Make the demo's temporary job name unique per invocation so it remains repeatable against persistent local data.
- Preserve and probe current job endpoint paths and response shapes.

## Phase results

- Job list/create/update now route through `JobService` and `JobRepository`.
- The parent POM now imports Spring Boot and Testcontainers BOMs, enforces Spotless formatting in `verify`, and manages JaCoCo, SpotBugs, PIT, and Failsafe versions.
- Java sources were formatted with Google Java Format; wildcard imports were removed from the current Java sources.
- The scheduler now takes due-schedule time from PostgreSQL (`now()`) instead of the JVM wall clock.
- `AGENTS.md` contains the full project rules, and GitHub Actions runs Maven `verify` plus frontend install/build.
- `JobServiceIT` is wired for PostgreSQL through Testcontainers, but this Windows Maven process could not discover Docker through the Desktop named pipe; `./mvnw -B verify` therefore fails before the test setup runs.
- `./mvnw -B -DskipITs verify` passes for compilation and the 3 existing unit tests, with the integration test explicitly skipped.
- Compose rebuild and runtime API probes passed. The 1,000-run demo passed after job creation was changed to use a unique name per invocation.
- Frontend `lint` and `test:ci` scripts still need implementation; `npm ci` and `npm run build` pass.
