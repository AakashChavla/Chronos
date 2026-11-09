# Testing

Run backend unit tests with `./mvnw test` and build the dashboard with `npm --prefix frontend run build`. The intended integration-test target is PostgreSQL through Testcontainers. Concurrency, lease expiry, retries, workflow ordering, and replay need database-backed tests before the prototype can claim their acceptance guarantees.
