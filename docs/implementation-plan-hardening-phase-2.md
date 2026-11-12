# Hardening Phase 2 implementation plan

## Goal

Make worker execution capacity bounded and safe under lease loss, shutdown, cancellation, timeout, retries, and concurrent claims. Preserve existing successful API paths; API cancellation changes RUNNING rows from immediate completion to a cancel request, as specified by the hardening plan.

## Planned files and changes

1. `docs/implementation-plan-hardening-phase-2.md`, `CHECKLIST.md`: implementation steps and exact acceptance evidence.
2. `core/src/main/resources/db/migration/V3__worker_correctness.sql`: forward-only additions for per-job backoff, priority, cancellation, and run events.
3. `core/src/main/java/io/chronos/core/run/**`, `core/src/main/java/io/chronos/core/config/ChronosProperties.java`: lease/cancel-aware repository operations, guarded state transitions, retry policy, run-event records, advisory-locked batch reaper, configurable worker limits, lease and heartbeat durations.
4. `worker/src/main/java/io/chronos/worker/**`, `common/src/main/java/io/chronos/common/RunContext.java`, `handlers/src/main/java/io/chronos/handlers/**`: semaphore-based capacity, poll jitter, SmartLifecycle shutdown/release, heartbeat cancellation, timeout/zombie handling, idempotent cancellation signal, retry/non-retryable outcomes, and worker metrics.
5. `api/src/main/java/io/chronos/api/run/**`: cancel behavior must request cancellation for a running run and directly cancel only a queued run.
6. `core/src/test/**`, `worker/src/test/**`, `api/src/test/**`: RetryPolicy unit/property tests, claim/reaper/lease-fencing Testcontainers tests, cancellation/timeout/capacity/lifecycle tests with deterministic clocks or controllable handlers, and atomic run-event assertions.
7. `CHECKLIST.md`: test each Phase 2 acceptance criterion and state remaining chaos/load limitations without implying unrun evidence.

## Phase 2 completion slice (this continuation)

1. `worker/src/main/java/io/chronos/worker/WorkerService.java` and `core/src/main/java/io/chronos/core/run/**`: track in-flight executions, jitter polling by +/-20%, implement `SmartLifecycle` shutdown with configured grace, and lease-fenced release without incrementing attempts.
2. Worker timeout path: interrupt at timeout, report handlers still alive after the five-second grace, keep the worker capacity slot occupied until the handler exits, and stop heartbeating so the reaper can recover the run without allowing a late result write.
3. Add a `NonRetryableException` handler outcome that transitions directly to FAILED, plus tests for state and event behavior.
4. Tests: unit tests for poll jitter bounds/lifecycle waiting/capacity, Testcontainers for shutdown release and terminal non-retryable failure. Do not claim SIGKILL, SIGSTOP, or cancellation latency criteria without running them.
5. No new dependency planned. Use Java concurrency primitives and existing logging; zombie metric export is deferred unless the worker app has a scrapeable metrics endpoint without adding a REST server.

## Dependency changes

Add `net.jqwik:jqwik` as a test dependency in `core` for the explicitly required transition and retry policy properties. Version `1.9.2` is pinned in this plan. No new runtime dependency is planned.

## Test and verification plan

- Unit: seeded retry delay/cap/jitter and terminal state properties; capacity accounting; cancellation signal and timeout behavior.
- PostgreSQL 16 Testcontainers: stale lease token fencing, simultaneous claimers, per-job concurrency, disabled/priority selection, retry versus DEAD, reaper advisory lock, cancellation, and run-event atomicity.
- Commands: `./mvnw -B verify`, `./mvnw -B dependency:analyze`, status-boundary script, Compose build and runtime checks, and targeted worker chaos/demo scenarios where the host permits.
- Do not claim SIGSTOP/SIGKILL, cancellation latency, or throughput acceptance without running its corresponding scenario.

## Compatibility

Existing successful paths remain unchanged. RUNNING cancellation will return an accepted cancellation request and complete as CANCELLED after the worker observes it; this is a behavior change required by Phase 2 and will be documented. Existing lease-token fencing and DB-time lease comparisons will be retained and tested.
