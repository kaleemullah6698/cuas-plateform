# platform-commons

Cross-cutting concerns shared by all twelve platform services, so that every
service behaves identically under observation (ARCHITECTURE.md section 4.3).

Scope: correlation-ID propagation, structured JSON logging, Micrometer metrics,
OpenTelemetry tracing, exception mapping, mTLS configuration, shared validation.

## Status

Scaffold only. Package structure exists; no implementations.
