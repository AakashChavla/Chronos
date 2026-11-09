# Phase 3: centralized run transitions and worker lease fencing

## Scope

Move API retry/cancel actions and worker claim/heartbeat/completion/failure/reaper updates behind one transition service. Add a per-claim lease token so a worker that lost its lease cannot write a result. Keep SQL in the repository.

## Files to add or change

- Add forward-only migration `V2__job_run_lease_token.sql` for the lease token.
- Add a run transition repository port and JDBC implementation under `core/.../run/repository/`.
- Add `RunTransitionService` under `core/.../run/service/`; validate expected transitions through `RunStateMachine` and accept worker writes only when the lease token matches.
- Add internal `core` module dependencies to `worker` and `scheduler` so worker and scheduler application services can share the run transition boundary.
- Upgrade the existing Testcontainers BOM and explicitly manage its JUnit/PostgreSQL modules at 1.21.4 because Spring Boot's BOM otherwise keeps them at 1.20.4. The newer client supports recent Docker Engine changes.
- Update the API retry/cancel methods and worker claim, heartbeat, completion, failure, and reaper paths to call that service.
- Add database-backed Testcontainers coverage for stale lease writes, concurrent claims, and lease expiry/requeue/dead-letter handling. No new dependency is planned.
- Update the walkthrough checklist with executed results and remaining unverified concurrency claims.

## Verification

- Run `./mvnw -B verify`; do not hide or skip integration failures.
- Run frontend npm CI commands, Compose rebuild, API probes, and the 1,000-run smoke demo.
- Report Windows Testcontainers discovery failures explicitly if they persist.
