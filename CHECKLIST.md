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
- [ ] demo verification (`bash scripts/demo.sh` could not start because this Windows host has no `/bin/bash`; Docker API returned HTTP 500 during rebuild)
- [x] README run steps and Mermaid architecture diagram

## Phase 1 verification evidence (2026-11-09)

- `./mvnw -B verify` — passed; 3 tests, 0 failures, 0 errors. The wrapper downloads Maven 3.9.9.
- `npm ci` — passed; 73 packages audited, 0 vulnerabilities.
- `npm run lint` — unavailable; the package has no `lint` script.
- `npm run test:ci` — unavailable; the package has no `test:ci` script.
- `npm run build` — passed; Vite produced the production bundle (with upstream `use client` directive warnings).
- `docker compose config --quiet` and `docker compose -f deploy/compose/docker-compose.yml config --quiet` — both passed.
- `docker compose up -d --build` — did not complete; Docker Desktop's Linux engine API returned HTTP 500 and then stopped responding.
- `bash scripts/demo.sh` — could not start; the host's WSL bash reports `/bin/bash` missing.
