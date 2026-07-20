# Backend Implementation Roadmap

Derived strictly from `docs/ARCHITECTURE.md`. Introduces no service, API, schema, or feature not in that document. Where this roadmap corrects the informal week-plan, the correction and its architectural justification are stated explicitly.

---

# Phase 1 — Architecture Analysis

## 1.1 Overall backend architecture

Twelve Spring Boot services plus two non-service Maven modules (`platform-parent`, `platform-commons`), each service owning its own database (§4.1). Services communicate two ways:

- **Synchronous** (gRPC/REST) only where latency demands it: gateway → auth/user for token and permission resolution; `track-correlation → ai-orchestrator` for inference; orchestrator → AI services.
- **Asynchronous** (Kafka) for the entire detection pipeline. This is the backbone: `sensor-ingestion → track-correlation → ai-orchestrator → threat-assessment → alert-service → notification`, with `audit-service` and `reporting-service` consuming across the board.

The pipeline is event-driven precisely so a slow stage degrades latency without stopping ingestion (§5.6).

## 1.2 Service dependencies

```
platform-parent  ──▶  (every module)
platform-commons ──▶  (every service)
contracts/proto  ──▶  serde, ai-orchestrator, sensor-ingestion, all event producers/consumers, frontend types

api-gateway      ──▶  auth-service (ValidateToken), user-service (ResolvePermissions)   [sync gRPC]
sensor-ingestion ──▶  Kafka
track-correlation──▶  sensor-ingestion (events), ai-orchestrator (sync Infer)
ai-orchestrator  ──▶  AI services or mock provider
threat-assessment──▶  track-correlation (events), ai-orchestrator (inference events)
alert-service    ──▶  threat-assessment (events)
notification     ──▶  alert-service (events)
audit-service    ──▶  consumes ALL — depends on nothing (never an availability dependency, §4.2)
reporting-service──▶  consumes ALL → Elasticsearch
config-service   ──▶  nothing; pushes to all via WatchConfig
```

## 1.3 Independently developable vs. dependent

| Can be built independently | Must follow a dependency |
|---|---|
| `platform-commons` (foundation) | `api-gateway` — needs auth + user gRPC |
| `auth-service` (self-contained) | `track-correlation` — needs ingestion events + orchestrator |
| `user-service` (self-contained) | `threat-assessment` — needs track + inference events |
| `audit-service` (pure consumer) | `alert-service` — needs threat events |
| `config-service` (pure producer) | `notification-service` — needs alert events |
| `sensor-ingestion` (once protos exist) | `ai-orchestrator` — needs the inference contract |

## 1.4 Critical contracts

1. **`DetectionRecord`** (§5.3) — the canonical record every modality normalises into. The sensor-agnosticism boundary. Nothing in the pipeline compiles without it.
2. **`InferenceService`** (§6.2) — the Track A / Track B boundary. Nothing in Track B exists without it; the mock provider cannot compile without it.
3. **Kafka event schemas** (§5.2) — the async contracts between every pipeline stage.
4. **JWT claims + JWKS** (§7.3) — the gateway validates against this without calling auth per request.

## 1.5 Shared modules

- `platform-parent` — dependency and plugin versions pinned in exactly one place.
- `platform-commons` — correlation-ID propagation, structured logging, Micrometer, OpenTelemetry, exception mapping, mTLS config, shared validation. Built once; every service behaves identically under observation (§4.3).

## 1.6 Infrastructure dependencies

PostgreSQL (10 schemas), Kafka + Schema Registry (KRaft, auto-create disabled), Redis, Elasticsearch, MinIO. All declared in `docker-compose.yml` with `infra`/`platform`/`ai` profiles. A single `docker compose --profile infra up -d` must bring the data layer up before any service starts.

## 1.7 Blockers — retire before implementation

**All of B1–B5 are now CLOSED.** B6 remains an open design risk, addressed at its milestone.

| # | Blocker | Impact | Retired in | Status |
|---|---|---|---|---|
| B1 | **`contracts/proto/*` empty.** `detection.proto`, `inference.proto`, event protos unwritten. | Nothing downstream compiles. The highest-value gap in the repo. | M1 | ✅ **Closed** — both protos authored, generating and round-tripping in Java/Python/TS |
| B2 | **Toolchain unverified.** No JDK/Maven on the generating machine; `mvn verify` and `npm ci` never run. | Build may fail on POM coordinates or dependency versions on first attempt. | M0 | ✅ **Closed** — JDK 21 + Maven 3.9.9; full reactor `clean install` green (16 modules, 38 tests) |
| B3 | **`platform-commons` empty.** Every service depends on it. | Services can't share cross-cutting concerns; duplication starts immediately if skipped. | M0 | ✅ **Closed** — implemented, 23/23 tests |
| B4 | **Kafka topics not declared.** `create-topics.sh` is a placeholder; auto-create is off by design. | Producers/consumers fail at runtime. | M1 | ✅ **Closed** — 24 topics created live, configs verified, produce/consume round-trip passes |
| B5 | **DB init absent.** `init-databases.sh` is a placeholder. | Services can't connect to their schema. | M0 | ✅ **Closed** — 10 databases created live on PostgreSQL 17, per-database connections verified |
| B6 | **`track-correlation` is stateful, sector-partitioned** (§4.1.5) — the hardest service. | Highest design risk; must not be rushed. | M5, deliberately isolated | ⬜ Open — by design, addressed at M5 |

**None can be worked around. M0 and M1 existed solely to retire B1–B5 before any business logic — all five are now closed, and the infrastructure is live and verified. M2 may begin.**

---

# Phase 2 — Development Timeline (Milestones)

Ten milestones. The mapping to your informal week-plan is shown, with corrections flagged.

| M | Objective | Services | Week-plan alignment |
|---|---|---|---|
| **M0** | Foundation & toolchain | `platform-parent`, `platform-commons`, infra | Week 1 |
| **M1** | Contracts & messaging substrate | `contracts`, Kafka topics, Schema Registry | Week 3 (pulled earlier — **correction 1**) |
| **M2** | Identity & audit | `auth-service`, `user-service`, `audit-service` | Week 2 (+ audit pulled in — **correction 2**) |
| **M3** | Edge | `api-gateway` | Week 2 |
| **M4** | Ingestion | `sensor-ingestion`, sensor simulator | Week 3 |
| **M5** | Correlation | `track-correlation` | Week 3 |
| **M6** | AI boundary | `ai-orchestrator`, `mock-inference-provider` | Week 5 (pulled before threat — **correction 3**) |
| **M7** | Assessment & alerting | `threat-assessment`, `alert-service`, `notification-service` | Week 4 |
| **M8** | Config, reporting, observability, security hardening | `config-service`, `reporting-service`, monitoring | Weeks 4–8, cross-cutting |
| **M9** | Real-time edge & thin frontend verification | gateway WebSocket, minimal frontend | Weeks 6–8 |

### Corrections to the week-plan, and why

1. **Contracts before Kafka/detection.** Your plan introduces Kafka in Week 3. But §5.2/§5.3 make Protobuf schemas the payload for every topic, and §6.2 makes the inference contract the Track-B boundary. Contracts must be frozen *first* (the architecture calls this the Week-2 freeze, §1.4). M1 does this.

2. **Audit-service moves up to M2, beside auth.** CLAUDE.md §5 and §7.5 require every mutation to be audited and to *fail closed* if it can't be. You cannot build auth's mutations (login, lockout, token revoke) correctly without the audit sink existing. Building auth first and retro-fitting audit would mean rewriting auth. So audit is a peer of auth, not a later add.

3. **AI orchestrator + mock before threat.** `threat-assessment` consumes `ai.inference.completed.v1` (§4.1.7). That event only exists once the orchestrator produces it. Your plan has threat (Week 4) before mock AI (Week 5); the dependency runs the other way. M6 (orchestrator + mock) precedes M7 (threat). This is also what makes the mock-first strategy real: threat is developed and tested against mocked inference from day one.

Each milestone's detailed spec (Phase 4) is produced **at the start of that milestone**, not now — per your Phase 5 instruction not to generate the whole project up front. M0 and M1 are fully detailed below because they are next.

### Per-milestone summary

Each milestone below carries: Objective · Services · Deliverables · Dependencies · Expected output · Testing strategy.

**M0 — Foundation & toolchain**
Objective: a buildable, observable skeleton with infrastructure running. Deliverables: pinned `platform-parent`; `platform-commons` (correlation filter, JSON logging, Micrometer, OTel, RFC-7807 handler, mTLS config); `init-databases.sh` creating 10 schemas; infra profile up; one service (`config-service` shell) building green to prove the chain. Dependencies: none. Expected output: `mvn -pl platform-commons verify` green; `docker compose --profile infra up -d` healthy. Testing: unit tests on commons utilities; a smoke test asserting a correlation ID propagates through a filter.

**M1 — Contracts & messaging substrate**
Objective: freeze every wire contract. Services: `contracts`. Deliverables: `detection.proto` (§5.3), `inference.proto` (§6.2), `track.proto`, `alert.proto` event schemas; `buf` lint + breaking + generate for Java/Python/TS; `create-topics.sh` declaring the full §5.2 catalogue; Schema Registry in `BACKWARD` mode. Dependencies: M0. Expected output: generated bindings in three languages; topics created; `buf breaking` wired into CI. Testing: `buf lint`; a round-trip serialise/deserialise test per message; a topic-existence check.

**M2 — Identity & audit**

Split into three sub-milestones, ordered by dependency (`contracts → audit → user → auth`): audit
is a pure event consumer that calls nothing, so it is buildable first and is the sink the other two
require; auth depends on user-service for permission resolution.

| Sub-milestone | Scope | Status |
|---|---|---|
| **M2.1** | Shared contracts (`audit.proto`, `Permission`/`Role`, audit publisher) + `audit-service` | ✅ **Complete** — 97 tests |
| **M2.2** | `user-service` — users, roles, effective-permission resolution, self-escalation and separation-of-duty enforcement | ✅ **Complete** — 39 tests |
| **M2.3** | `auth-service` — Argon2id, TOTP MFA, RS256 JWT, rotating refresh with reuse detection | ✅ **Complete** — 37 tests |

**M2 complete.** `mvn clean verify`: 16 modules, **173 tests**, 0 failures.

**M2.2 delivered:** `User` aggregate with role membership; `RoleAssignmentPolicy` enforcing the two §7.4 constraints (an actor cannot grant a permission they lack; no account may hold both `role:write` and `audit:export`); effective-permission resolution that returns nothing for a deactivated account; `ResolvePermissions` gRPC; REST administration with mandatory justification on every role change; RBAC reference tables synchronised from the enums at startup rather than duplicated in SQL. Every mutation publishes through the fail-closed `AuditPublisher`, and rollback is proven by test.

**M2.3 delivered:** Argon2id (§7.3 parameters); RFC 6238 TOTP with ±1-step skew and replay blocked by recording the accepted time step; RS256 access tokens carrying the resolved `perms` claim, with JWKS published for verifiers; rotating refresh tokens stored only as digests, with reuse detection revoking the whole family and its session; account lockout; `HttpOnly`/`Secure`/`SameSite=Strict` refresh cookie; deactivation consumer revoking sessions on `platform.user.deactivated.v1`.

**Three defects found and fixed during M2.2/M2.3:**
- **Security bookkeeping was rolled back by its own failure.** Failed-attempt records, the lockout counter, and — most seriously — the refresh-token family revocation on reuse detection all ran inside the transaction that the rejection exception rolled back. Reuse detection therefore reported an attack and then took no action, leaving the captured family live. Moved to `SecurityBookkeeping` with `REQUIRES_NEW`; four tests now pin the behaviour.
- **`user_roles.assigned_by` duplicated `role_change_log`** and was unwritable through the element-collection mapping. Removed: who granted a role, when, and why is recorded once.
- **Spring Data does not register repositories nested inside a holder class**, so grouping them silently produced no beans. Split into top-level interfaces.

**Documented amendment:** §5.2 gained `platform.user.deactivated.v1`, which §4.1.2 requires auth-service to consume but the catalogue omitted. Added to `create-topics.sh`. Other domain events §4.1.2/§4.1.3 list remain unbuilt — they have no consumer yet, per the M1 precedent of not shipping unconsumed contracts.

**M2.1 delivered:** `audit.proto` (fields from the §7.5 table); `Permission`/`Role` encoding the
§7.4 matrix with the ADMIN-cannot-`audit:export` separation-of-duty invariant asserted at startup;
fail-closed `AuditPublisher` (synchronous send — an action that cannot be recorded does not
happen); `audit-service` with a Flyway migration carrying the §7.5 schema and the
`DO INSTEAD NOTHING` immutability rules, a length-delimited SHA-256 hash chain, advisory-lock
serialised append, Kafka consumer, and an RBAC-gated query/verify API.

**Two defects found and fixed during M2.1:**
- *Timestamp precision.* `occurredAt` was hashed at nanosecond precision but PostgreSQL
  `TIMESTAMPTZ` stores microseconds, so any sub-microsecond event failed its own verification on
  reload — a false tamper alarm. Timestamps are now truncated to microseconds at construction, and
  a regression test pins it.
- *Testcontainers on Engine 29.* docker-java negotiates API 1.32, which Engine 29 rejects with
  HTTP 400. Pinned to 1.41 via the `api.version` system property in `platform-parent`.

Objective: authenticate, authorise, and record. Services: `auth-service`, `user-service`, `audit-service`. Deliverables: Argon2id credentials, TOTP MFA, RS256 JWT issue/rotate/revoke with reuse detection; the §7.4 RBAC model with self-escalation and separation-of-duty enforcement; hash-chained append-only audit log with checkpointing and chain-verify. Dependencies: M0, M1. Expected output: login→MFA→token flow; permission resolution; every mutation lands an audit record; chain verifies. Testing: domain unit tests for token/rotation rules; Testcontainers for persistence and the audit chain; endpoint tests for success/400/403; an idempotency test on audit writes.

**M3 — Edge (gateway)**
Objective: single authenticated ingress. Services: `api-gateway`. Deliverables: JWKS-cached JWT validation, permission pre-check, routing, §7.8 rate limiting, correlation-ID injection. Dependencies: M2. Expected output: authenticated requests reach a stub route; unauthenticated rejected; rate limits enforced. Testing: `@WebMvcTest` filters; rate-limit integration test against Redis; a token-revocation propagation test.

**M4 — Ingestion**
Objective: heterogeneous sensor input → canonical `DetectionRecord` on Kafka. Services: `sensor-ingestion`, sensor simulator. Deliverables: REST + gRPC intake, per-modality normalisation, UTC + drift correction, dedup by `(sensorId, sensorSeq)`, health telemetry, dataset replay. Dependencies: M1. Expected output: replayed dataset traffic emerges on `sensor.detection.normalized.v1`. Testing: normalisation unit tests per modality; a dedup test; a Testcontainers Kafka round-trip; back-pressure drop-order test (§5.6).

**M5 — Correlation**
Objective: detections → tracks. Services: `track-correlation` (the stateful, sector-partitioned service — isolated deliberately). Deliverables: spatio-temporal association, Kalman state, lifecycle (tentative→confirmed→coasting→lost), CPA to assets, gRPC Infer call to orchestrator (circuit-broken). Dependencies: M4, and M6's contract (calls orchestrator). Expected output: `track.created/updated/lost` events from replayed detections. Testing: heavy domain unit tests on association and Kalman; a lifecycle-transition suite; integration test consuming real detection events.

**M6 — AI boundary**
Objective: the Track-A/Track-B decoupling, working with zero models. Services: `ai-orchestrator`, `mock-inference-provider`. Deliverables: modality routing, per-service timeout + Resilience4j breaker + bulkhead, content-hash cache, canary split, mock fallback publishing `ai.service.degraded.v1`; deterministic latency-realistic failure-injecting mock badged `0.0.0-mock`. Dependencies: M1. Expected output: `ai.inference.completed.v1` produced from mock; breaker opens and falls back under injected failure. Testing: the shared conformance suite (pytest) against the mock; breaker/bulkhead unit tests; a fallback integration test.

**M7 — Assessment & alerting**
Objective: tracks + inference → scored threats → alerts → incidents → notifications. Services: `threat-assessment`, `alert-service`, `notification-service`. Deliverables: composite scoring (rules-dominant, explainable, §6.3.4), Low/Med/High/Critical, advisory recommendations from the **closed** vocabulary; alert lifecycle, escalation timers, incidents, evidence bundles; multi-channel delivery. Dependencies: M5, M6. Expected output: a replayed intrusion drives an end-to-end alert with a full decision chain. Testing: scoring rule unit tests; alert dedup + escalation integration tests; an idempotency test on threat→alert.

**M8 — Config, reporting, observability, hardening**
Objective: runtime configurability, searchable history, full telemetry, security pass. Services: `config-service`, `reporting-service`, monitoring stack. Deliverables: WatchConfig push; ES indexing + templated async reports + ILM retention (§7.9); Prometheus/Grafana/Loki/Tempo with the four §8.6 dashboards; a security-hardening review against §7. Dependencies: M2–M7. Expected output: a config change propagates without redeploy; reports generate; traces span end-to-end. Testing: config-propagation integration test; report-generation test; a trace-continuity assertion.

**M9 — Real-time edge & thin frontend verification**
Objective: prove the backend is usable end-to-end. Deliverables: gateway WebSocket fan-out (snapshot+delta, seq-gap, heartbeat) from `track.updated`/`alert.raised`; a *minimal* frontend — login, a live track list, an alert strip — solely to verify the APIs and the stream. Dependencies: M3, M7. Expected output: a browser shows live tracks and a real alert driven by replayed data through mocked inference. Testing: a WebSocket integration test; one Playwright E2E of the intrusion→alert flow.

---

# Phase 3 — Backend-First Confirmation

Frontend work is deferred to M9 and is deliberately minimal — enough to *verify* the backend, not to build the console. The §2 UI/UX specification and the full feature set are out of scope for this backend-first pass and remain for a later frontend phase. The design-system deliverable (§2.1) is explicitly **not** started here.

Priority order (your Phase-3 list, reconciled with the dependency graph): commons → contracts → infrastructure → identity+audit → gateway → ingestion → correlation → AI boundary (mock) → assessment/alerting → config/reporting/observability → security hardening → testing throughout. This is exactly M0→M9.

---

# Phase 4 — Granular Breakdown (M0 and M1 only)

Later milestones receive this treatment at their start (Phase 5 rule). Detailing them now would be speculation.

## Milestone M0 — Foundation & toolchain

**Goal:** a green build of `platform-commons`, infrastructure running, DB schemas created — the chain that everything else hangs on.

**Modules touched:** `backend/platform-parent`, `backend/platform-commons`, `infrastructure/postgres`, `docker-compose.yml`.

**`platform-commons` packages & classes:**

| Package | Class / interface | Responsibility |
|---|---|---|
| `commons.correlation` | `CorrelationId` | Holder + accessor for the current correlation ID (MDC-backed) |
| `commons.correlation` | `CorrelationIdFilter` | Injects/propagates `X-Correlation-Id` on every request |
| `commons.logging` | `LoggingConfig` | Structured JSON logging (Logback) with correlation ID |
| `commons.metrics` | `MetricsConfig` | Micrometer + Prometheus registry defaults |
| `commons.tracing` | `TracingConfig` | OpenTelemetry propagation wiring |
| `commons.exception` | `ProblemDetailFactory` | RFC-7807 construction with correlation ID |
| `commons.exception` | `BaseExceptionHandler` | `@RestControllerAdvice` base for services to extend |
| `commons.exception` | `DomainException` (sealed) | Root of the domain exception hierarchy |
| `commons.security` | `MtlsClientConfig` | Shared mTLS setup for inter-service clients |
| `commons.validation` | `ValidationMessages` | Shared constraint message keys |

**DTOs / records:** `ProblemResponse` (RFC-7807 shape). **Repositories:** none. **DB schema:** none in commons; `init-databases.sh` creates `authdb, userdb, sensordb, trackdb, threatdb, alertdb, notifydb, reportdb, auditdb, configdb`. **Kafka topics:** none. **Endpoints:** none. **Events:** none. **Config:** `platform-parent` pins Java 21, Spring Boot 3.4.1, gRPC, Protobuf, springdoc, Resilience4j, Testcontainers, MapStruct, JaCoCo versions.

**Unit tests:** `CorrelationIdFilterTest` (propagation + generation), `ProblemDetailFactoryTest` (7807 shape + correlation ID), `BaseExceptionHandlerTest`. **Integration tests:** a minimal Spring-context test proving auto-config loads; `docker compose --profile infra up -d` health check.

**Completion checklist:**
- [x] `mvn -pl platform-commons -am verify` green — BUILD SUCCESS, 23/23 tests
- [x] JDK 21 + Maven confirmed present (retires B2); `docs/DEVELOPMENT.md` verification table updated
- [x] All 10 databases created — `init-databases.sh` ran live against PostgreSQL 17 on first init; per-database connections verified (**retires B5**)
- [x] Correlation ID flows through the filter in a test (`CorrelationIdFilterTest`)
- [x] Infra profile healthy — all 7 containers up; `scripts/verify-infra.sh` reports **33 passed, 0 failed**

**Implementation notes — deviations from the pre-implementation sketch above (docs kept in sync with code):**
- `DomainException` is an **open abstract class, not `sealed`.** A sealed root cannot be extended by the services that must define their own domain exceptions; sealing it was a specification error, corrected here.
- No bespoke `ProblemResponse` record. Spring's built-in `org.springframework.http.ProblemDetail` is the single RFC-7807 implementation, produced by `ProblemDetailFactory`; a parallel record would duplicate it (violates CLAUDE.md §3).
- Structured logging ships as a logback include (`logback-json.xml` + logstash encoder), not a `LoggingConfig` Java class — logging configuration is declarative.
- `TracingConfig` (OpenTelemetry) **deferred to M8**, where end-to-end tracing is actually exercised. `MtlsClientConfig` **deferred to M6**, where the first inter-service client exists. `ValidationMessages` **deferred** until a genuinely shared constraint exists. Building them now would be placeholder code with no consumer (violates the "no placeholder implementations" rule).
- Delivered classes: `CorrelationId`, `CorrelationIdFilter`, `DomainException`, `ProblemDetailFactory`, `BaseExceptionHandler`, `MetricsConfig`, `CommonsAutoConfiguration` (Spring Boot auto-configuration, activates by dependency alone).

**Definition of Done: MET ✅.** Commons builds and tests green (23/23). Infra brought up and verified live — Postgres, Kafka, Schema Registry, Redis, Elasticsearch, and MinIO all reachable and functional; all 10 databases created. Blockers **B2, B3, B5 closed**.

## Milestone M1 — Contracts & messaging substrate

**Goal:** every wire contract frozen and code-generated; Kafka topics declared.

**Files to create:**

| File | Source | Contents |
|---|---|---|
| `contracts/proto/detection.proto` | §5.3 verbatim | `DetectionRecord`, `SensorModality`, `GeoPosition`, `Kinematics`, `Measurements` + per-modality messages |
| `contracts/proto/inference.proto` | §6.2 verbatim | `InferenceService`, `InferenceRequest/Response`, `Prediction`, `ConfidenceReport`, `ModelInfo` (+ 5 completed request/response types) |
| ~~`contracts/proto/track.proto`~~ | §5.2 | **Deferred to M5** — §5.2 gives topic names, not message field schemas; authored when track-correlation defines them |
| ~~`contracts/proto/alert.proto`~~ | §5.2 | **Deferred to M7** — same reason; authored when alert-service defines them |
| `infrastructure/kafka/create-topics.sh` | §5.2 catalogue | Every topic with its partition count, retention, cleanup policy |

**Packages generated:** `pk.nust.cuas.detection.v1`, `pk.nust.cuas.inference.v1` (Java); mirror in Python and TS under `contracts/generated/`. (`track.v1` / `alert.v1` follow at M5 / M7.)

**Kafka topics:** the full §5.2 table — `sensor.detection.raw/normalized`, `sensor.health`, `track.*`, `ai.inference.*`, `threat.*`, `alert.*`, `incident.created`, `platform.config.updated`, `platform.audit`, and `*.DLQ`. Auto-create stays disabled.

**Tests:** `buf lint` clean; `buf breaking --against main` wired to CI; a round-trip serialise/deserialise test per top-level message in Java; a script asserting every declared topic exists after `create-topics.sh`.

**Completion checklist:**
- [x] `detection.proto` + `inference.proto` authored verbatim from §5.3/§6.2 (retires B1)
- [x] Bindings generated for Java, Python, TS via `buf generate`
- [x] `buf lint` green (documented exceptions for the fixed contract); `buf breaking` configured
- [x] All §5.2 topics created live — **24 topics** (18 primary + 6 DLQ); partitions, retention, and cleanup policy verified against the catalogue; produce/consume round-trip passes; idempotent on re-run (**retires B4**)
- [x] Round-trip test per message passes — Java (15 tests), Python (import + round-trip + gRPC stubs), TypeScript (strict `tsc`)

**Implementation notes — deviations from the sketch above (docs kept in sync with code):**
- **`track.proto` and `alert.proto` deferred to M5 / M7, not authored in M1.** §5.2 specifies only topic names and keys, not the message field schemas for track/alert events. Authoring them now would require inventing fields, which the milestone has no authority for ("do not invent"). Their schemas are defined at the milestone that owns the producing domain model (track-correlation at M5, alert-service at M7) and added to ARCHITECTURE.md there. This does not block M1: topic *creation* needs only names, which are fully specified.
- **Five referenced-but-undefined inference messages completed.** §6.2's `InferenceService` referenced `BatchInferenceRequest`, `BatchInferenceResponse`, `ModelInfoRequest`, `HealthRequest`, `HealthResponse` without bodies; the proto would not compile. Completed minimally and §6.2 amended to match.
- **buf lint exceptions.** Six STANDARD rules conflict with the deliberately-fixed contract (enum-value prefixes, RPC request/response naming, shared stream types, flat directory layout preserving `import "detection.proto"`). Excepted with documented rationale in `contracts/buf.yaml`; breaking-change detection is unaffected.
- **DLQ interpretation.** §5.2's single `*.DLQ` row is realised as one `<topic>.dlq` per consumed pipeline topic (6 total) — the hops where a poison message must be quarantined (§5.6).
- **Java bindings compiled via a Maven module** (`backend/contracts`) that runs `buf generate` in `generate-sources` and compiles `contracts/generated/java`. protobuf-java pinned to 4.29.2 platform-wide (overriding the Spring Boot BOM) to match the protoc v29.2 plugins.

**Definition of Done: MET ✅.** The two critical contracts exist and generate cleanly in three languages, each verified to compile/import/round-trip. The topic catalogue is declarative and **applied live against Kafka (24 topics, configs verified)**. `buf breaking` is configured for CI. Blocker **B4 closed**. Track A and Track B can now proceed in parallel against a frozen contract.

---

# Phase 5 — Execution Protocol

Implementation begins **only after this roadmap is approved**, one milestone at a time, never the whole project at once.

Each milestone opens with: (1) architecture analysis, (2) dependencies, (3) reusable components, (4) implementation plan — then implements only that milestone. Every implementation obeys CLAUDE.md and the standards in `docs/CODING-STANDARDS.md`: Java 21, Clean Architecture, DDD, SOLID, database-per-service, constructor injection, immutable domain models, DTO separation, RFC-7807, OpenAPI, validation, correlation IDs, RBAC, audit logging, and comprehensive tests. No API, service, or schema outside `docs/ARCHITECTURE.md`.
