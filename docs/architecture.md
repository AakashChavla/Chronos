# Architecture

```mermaid
flowchart LR
  UI[React dashboard] --> API[Spring Boot API]
  API --> DB[(PostgreSQL queue, state and coordination)]
  SCH[Scheduler leader] --> DB
  W1[Worker 1] --> DB
  W2[Worker 2] --> DB
  W3[Worker 3] --> DB
  API -->|SSE| UI
  API --> P[Prometheus]
  P --> G[Grafana]
```

The API owns REST, authentication boundaries, SSE, and Flyway startup. The scheduler coordinates due jobs and workflow progression through PostgreSQL. Workers claim work with row locks and leases, so PostgreSQL is both queue and coordination store. `common` contains pure Java state primitives; `core` contains shared persistence and application services; `handlers` contains the handler SPI and built-ins.
