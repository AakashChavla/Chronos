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
