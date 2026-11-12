# Chronos

## Project structure

- `common` contains shared run status and execution context types.
- `core` owns job, run, workflow, scheduler, and worker domain services and PostgreSQL repositories. Flyway migrations live in `core/src/main/resources/db/migration` and currently run from the API application.
- `handlers` contains the job handler SPI and built-in handlers.
- `api` serves the HTTP API, SSE stream, and metrics.
- `scheduler` contains cron and workflow progression.
- `worker` claims and executes queued runs.
- `frontend` is the React/Vite console.

Compose builds three distinct Spring Boot applications from the shared Dockerfile: `api` owns the REST API, metrics, and Flyway migrations; `scheduler` advances cron fires and workflows; `worker` claims and executes runs. Three worker containers run as headless processes without HTTP listeners.

Distributed PostgreSQL-backed job scheduler and workflow prototype.

```mermaid
flowchart LR
 UI[React dashboard] --> API[Spring API]
 API --> DB[(PostgreSQL queue + coordination)]
 SCH[Scheduler app] --> DB
 W1 & W2 & W3[Virtual-thread worker apps] --> DB
 P[Prometheus] --> API
 G[Grafana] --> P
```

Run `docker compose up --build`, then open http://localhost:15173. The API is on port 18080, Prometheus on 19090 and Grafana on 13000 because the default host ports were already occupied on the setup host. PostgreSQL stays internal to the Compose network. Run `bash scripts/demo.sh` after the stack is healthy.

### Runtime profiles and worker settings

The Spring profile defaults to the app-specific configuration. Activate `dev`, `test`, or `prod` with `SPRING_PROFILES_ACTIVE`; profile files set polling defaults, and validated `ChronosProperties` reject out-of-range values at startup.

| Setting | Default | Purpose |
| --- | ---: | --- |
| `chronos.worker.id` (`WORKER_ID`) | hostname or `local` | Worker identity registered in PostgreSQL |
| `chronos.worker.enabled` | app-specific | Enables polling and execution in the worker app |
| `chronos.worker.poll-interval-ms` | `1000` | Delay between worker polls; valid range 100–60000 ms |
| `chronos.worker.lease-duration-ms` | `15000` | Lease set on a newly claimed run; valid range 1000–300000 ms |
| `chronos.worker.batch-size` | `10` | Maximum queued runs claimed per poll; valid range 1–1000 |
| `chronos.scheduler.enabled` | app-specific | Enables scheduler ticks in the scheduler app |
| `chronos.scheduler.poll-interval-ms` | `1000` | Delay between scheduler ticks; valid range 100–60000 ms |

The queue uses `FOR UPDATE SKIP LOCKED`, worker leases, reaping, and per-job running-count admission. API errors for invalid workflow graphs are RFC 7807-style 422 responses.
