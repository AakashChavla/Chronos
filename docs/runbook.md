# Local runbook

Start the local stack with `docker compose up --build -d`. Check state with `docker compose ps` and application logs with `docker compose logs -f app worker1 worker2 worker3`. Stop it with `docker compose down`; add `-v` only when you intend to delete local database data.

- Dashboard: http://localhost:15173
- API: http://localhost:18080
- Prometheus: http://localhost:19090
- Grafana: http://localhost:13000
