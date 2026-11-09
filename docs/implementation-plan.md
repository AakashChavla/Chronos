# Phase 1: repository structure and build wiring

## Scope

Establish the requested repository layout while keeping the current prototype runnable. This phase does not claim the full concurrency acceptance suite or a completed feature-by-feature extraction.

## Planned files

- Change the root and API Maven POMs to add `core` and its API runtime dependency.
- Add only two internal Maven references: `core` uses JDBC/Spring context for shared persistence services, and `api` uses `core` so Flyway can find migrations through the runtime classpath.
- Add `core/pom.xml` and a minimal module marker; place the versioned schema migration in `core/src/main/resources/db/migration` for API-owned Flyway startup.
- Add repository guidance and project policy files: `AGENTS.md`, `CONTRIBUTING.md`, `SECURITY.md`, `CHANGELOG.md`, and `LICENSE`.
- Add architecture, testing, threat-model, runbook, performance, API, and ADR docs under `docs/`.
- Add `deploy/`, `e2e/`, `load/`, and frontend feature-folder scaffolding. Keep root `docker-compose.yml` authoritative.
- Add package-level architecture notes under the requested core and API feature areas; leave implementation extraction for later phases.
- Update `CHECKLIST.md` with commands actually run and unavailable acceptance coverage.
- Update `.gitignore` to exclude the generated repository inventory artifact from the phase commit.
- Make `scripts/demo.sh` create a dedicated sleep handler job with a high concurrency limit, throttle submissions to five clients, and assert exactly 1,000 rows all succeeded on the first attempt.
- Add the Maven wrapper and pin its Maven distribution.

## Verification

- Run `./mvnw -B verify` and record its result.
- Run frontend `npm ci`, lint, test, and build; record missing scripts or failures.
- Rebuild and start Docker Compose, then run `bash scripts/demo.sh` if the Docker engine responds.
- Report acceptance criteria only when the corresponding automated test or demo assertion has actually run.
