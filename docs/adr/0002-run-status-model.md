# ADR 0002: Run status model

## Status

Accepted for the prototype; production hardening is in progress.

## Context

Jobs are delivered at least once through PostgreSQL. Workers claim queued runs under a lease, and the reaper may queue a run again after a lease expires. Status changes need one shared policy.

## Decision

`RunTransitionService` is the application boundary for job-run status changes. It checks the expected transition through `RunStateMachine`, and its repository statements guard the current database status and lease ownership where applicable.

The supported statuses are:

- `QUEUED`: eligible for a worker when `run_at` is due.
- `RUNNING`: claimed by a worker under a lease.
- `SUCCEEDED`: handler completed successfully; terminal.
- `CANCELLED`: cancellation completed; terminal.
- `SKIPPED`: a workflow step was not run because a dependency failed; terminal.
- `DEAD`: retryable failures exhausted the attempt limit; manually retryable.
- `FAILED`: reserved for non-retryable failures; manually retryable. The current built-in worker does not yet distinguish this from retryable errors.

A retryable execution failure returns to `QUEUED` with an incremented attempt until the attempt limit is reached, at which point it becomes `DEAD`. At-least-once delivery is explicit; exactly-once execution is not promised.

## Alternatives

- Let controllers, scheduler, and workers update status independently: rejected because their transition rules could diverge.
- Treat a database claim as exactly-once execution: rejected because a process may fail after the handler's side effect and before completion is stored.

## Consequences

Status policy is centralized, while SQL remains in run repositories. Lease fencing prevents a worker with an old token from overwriting a later claim. The repository still contains status update statements, so a source-level architecture check is required to ensure those statements are only reached through `RunTransitionService`.

## When to revisit

Revisit when non-retryable handler failures and workflow-run transitions receive explicit policies and event-history tests.
