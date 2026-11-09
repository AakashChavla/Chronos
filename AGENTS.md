# Repository guidance

- Keep business rules in domain and service code; keep SQL in repository classes.
- Use constructor injection and Java records for API request/response DTOs.
- Keep schema changes in versioned Flyway migrations under `core/src/main/resources/db/migration`.
- Run `./mvnw test` for backend changes and `npm --prefix frontend run build` for frontend changes.
- Do not commit credentials, generated build output, or local environment files.
