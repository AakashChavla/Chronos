# Threat model

Chronos accepts job payloads and handler configuration over an HTTP API. Treat both as untrusted input. Restrict network access, validate payload sizes and handler settings, and do not place secrets in job payloads. Production authentication, authorization, secret storage, audit retention, and tenant isolation require deployment-specific review before exposing the prototype outside a trusted network.
