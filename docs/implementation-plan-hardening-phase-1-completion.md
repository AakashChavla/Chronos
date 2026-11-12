# Hardening Phase 1 continuation plan

## Goal

Close remaining Phase 1 foundation gaps without changing successful API paths or response shapes. The previous implementation already extracted job CRUD/trigger and workflow create/trigger. This continuation targets the remaining run, worker, and SSE endpoints, runtime configuration, architecture enforcement, and build acceptance evidence.

## Planned files and changes

1. `docs/implementation-plan-hardening-phase-1-completion.md` and `CHECKLIST.md`: this plan and criterion-by-criterion evidence; keep historical results distinct from current runs.
2. `api/src/main/java/io/chronos/api/ApiController.java`: remove the legacy all-purpose controller after extracting run endpoints, worker listing, replay, and stream APIs into feature packages.
3. `api/src/main/java/io/chronos/api/{run,worker,stream}/**`: add request/response records, controllers, services, and repository interfaces/SQL implementation; use `RunTransitionService` for status changes and RFC 7807 stable errors.
4. `core/src/main/java/io/chronos/core/config/**`, runnable app configs, and module `application.yml`: validated records for polling, lease, batch and worker identity; dev/test/prod profiles with safe defaults.
5. `core/src/test/**`, `api/src/test/**`, and `scripts/**`: configuration validation tests, error mapping tests, status-write enforcement (including inserts and SQL aliases), feature-service/controller tests, and feature-layer architecture checks.
6. `docs/adr/0002-run-status-model.md`, `README.md`, `CHECKLIST.md`: document status semantics, project module structure, and exact acceptance results.
7. `pom.xml` / module POMs only if needed for the listed tests; every dependency change will be recorded here with its reason. First inspect `dependency:analyze` reports and remove only confirmed unused declarations.

## Tests and evidence

- Unit tests: run state transitions and illegal transitions; config binding/validation; RFC 7807 code/status mappings; route behavior for run retry/cancel/replay and errors; package layering and no-controller-SQL checks.
- PostgreSQL 16 Testcontainers tests: extracted repository/service behavior and relevant job/run transitions.
- Acceptance commands: `./mvnw -B verify`, `./mvnw -B dependency:analyze`, `docker compose config --quiet`, `docker compose up -d --build`, status-write boundary script, live endpoint probes, and `scripts/demo.sh`.
- Do not claim any criterion complete unless the matching test or runtime probe passes. If a listed Phase 1 acceptance depends on earlier pre-refactor evidence that cannot be reconstructed, record that limitation instead of fabricating evidence.

## Compatibility and dependencies

No successful API route or response shape is intentionally changed. New feature packages preserve existing paths. No new dependency is planned unless architecture tests require ArchUnit; if added, its exact artifact and justification will be recorded here before adding it.
