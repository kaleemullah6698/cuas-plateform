# API Reference

## Status

Placeholder. No endpoints are implemented.

`ARCHITECTURE.md` §4.1 lists the intended API surface for each of the twelve services; §10 (unwritten) will hold worked contract examples.

---

## Conventions all endpoints follow

Binding once implementation begins. See [CODING-STANDARDS.md](CODING-STANDARDS.md) §2.

### Transport

- REST over HTTPS for operator-facing and administrative APIs
- gRPC for inter-service calls and all AI inference
- WebSocket (WSS) for the real-time stream, multiplexed by channel

### Base path

```
/api/v1/<resource>
```

Major version in the path. A breaking change mints `/api/v2/`; both run in parallel during migration.

### Errors

RFC 7807 `application/problem+json`:

```json
{
  "type": "https://cuas.nust.edu.pk/errors/validation-failed",
  "title": "Validation failed",
  "status": 400,
  "detail": "field 'sensorId' must not be blank",
  "instance": "/api/v1/ingest/detections",
  "correlationId": "01J8X4K2M9P3Q7R5T1V6W8Y0Z2"
}
```

`correlationId` is present on every error and traces the request end-to-end across services.

### Status codes

| Code | Meaning |
|---|---|
| 200 / 201 / 202 | Success; 202 for accepted async work (report generation) |
| 400 | Validation failure |
| 401 | Absent or invalid authentication |
| 403 | Authenticated but not permitted (§7.4 RBAC matrix) |
| 404 | Not found, or not visible at the caller's permission level |
| 409 | State conflict |
| 429 | Rate limited — carries `Retry-After` (§7.8) |
| 500 | Server fault; never exposes internal detail |

### Authentication

`Authorization: Bearer <accessToken>` — 15-minute JWT. Refresh via `HttpOnly` cookie against `POST /api/v1/auth/refresh`; refresh tokens rotate, and reuse revokes the entire token family (§7.3).

### Pagination

```
GET /api/v1/tracks?page=0&size=50&sort=threatScore,desc
```

Responses carry `page`, `size`, `totalElements`, `totalPages`.

### OpenAPI

Each service publishes `/v3/api-docs` and `/swagger-ui.html`. The gateway aggregates them.

---

## Contract definitions

Wire formats are defined in Protobuf under `contracts/proto/`, not in this document:

| Contract | Specification |
|---|---|
| `DetectionRecord` | ARCHITECTURE.md §5.3 |
| `InferenceService` | ARCHITECTURE.md §6.2 |
| Kafka event schemas | ARCHITECTURE.md §5.2 |
