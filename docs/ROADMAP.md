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

| # | Blocker | Impact | Retired in |
|---|---|---|---|
| B1 | **`contracts/proto/*` empty.** `detection.proto`, `inference.proto`, event protos unwritten. | Nothing downstream compiles. The highest-value gap in the repo. | M1 |
| B2 | **Toolchain unverified.** No JDK/Maven on the generating machine; `mvn verify` and `npm ci` never run. | Build may fail on POM coordinates or dependency versions on first attempt. | M0 |
| B3 | **`platform-commons` empty.** Every service depends on it. | Services can't share cross-cutting concerns; duplication starts immediately if skipped. | M0 |
| B4 | **Kafka topics not declared.** `create-topics.sh` is a placeholder; auto-create is off by design. | Producers/consumers fail at runtime. | M1 |
| B5 | **DB init absent.** `init-databases.sh` is a placeholder. | Services can't connect to their schema. | M0 |
| B6 | **`track-correlation` is stateful, sector-partitioned** (§4.1.5) — the hardest service. | Highest design risk; must not be rushed. | M5, deliberately isolated |

**None can be worked around. M0 and M1 exist solely to retire B1–B5 before any business logic.**

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
- [ ] `mvn -pl platform-commons -am verify` green
- [ ] JDK 21 + Maven confirmed present (retires B2); `docs/DEVELOPMENT.md` verification table updated
- [ ] Infra profile healthy; all 10 schemas created (retires B5)
- [ ] Correlation ID flows through the filter in a test

**Definition of Done:** commons builds and tests green; a fresh `docker compose --profile infra up -d` reaches healthy on Postgres, Kafka, Redis, Elasticsearch; the verification-status table reflects what actually ran.

## Milestone M1 — Contracts & messaging substrate

**Goal:** every wire contract frozen and code-generated; Kafka topics declared.

**Files to create:**

| File | Source | Contents |
|---|---|---|
| `contracts/proto/detection.proto` | §5.3 verbatim | `DetectionRecord`, `SensorModality`, `GeoPosition`, `Kinematics`, `Measurements` + per-modality messages |
| `contracts/proto/inference.proto` | §6.2 verbatim | `InferenceService`, `InferenceRequest/Response`, `Prediction`, `ConfidenceReport`, `ModelInfo` |
| `contracts/proto/track.proto` | §5.2 | `TrackCreated/Updated/Lost` event payloads |
| `contracts/proto/alert.proto` | §5.2 | `ThreatAssessed`, `ThreatLevelChanged`, `AlertRaised`, `IncidentCreated` |
| `infrastructure/kafka/create-topics.sh` | §5.2 catalogue | Every topic with its partition count, retention, cleanup policy |

**Packages generated:** `pk.nust.cuas.detection.v1`, `pk.nust.cuas.inference.v1`, `pk.nust.cuas.track.v1`, `pk.nust.cuas.alert.v1` (Java); mirror in Python and TS under `contracts/generated/`.

**Kafka topics:** the full §5.2 table — `sensor.detection.raw/normalized`, `sensor.health`, `track.*`, `ai.inference.*`, `threat.*`, `alert.*`, `incident.created`, `platform.config.updated`, `platform.audit`, and `*.DLQ`. Auto-create stays disabled.

**Tests:** `buf lint` clean; `buf breaking --against main` wired to CI; a round-trip serialise/deserialise test per top-level message in Java; a script asserting every declared topic exists after `create-topics.sh`.

**Completion checklist:**
- [ ] `detection.proto` + `inference.proto` authored from §5.3/§6.2 (retires B1)
- [ ] Bindings generated for Java, Python, TS
- [ ] `buf lint` + `buf breaking` green and in CI
- [ ] All §5.2 topics declared and created (retires B4)
- [ ] Round-trip test per message passes

**Definition of Done:** the two critical contracts exist and generate cleanly in three languages; the topic catalogue is declarative and applied; CI fails on an incompatible schema change. At this point Track A and Track B can proceed in parallel against a frozen contract.

---

# Phase 5 — Execution Protocol

Implementation begins **only after this roadmap is approved**, one milestone at a time, never the whole project at once.

Each milestone opens with: (1) architecture analysis, (2) dependencies, (3) reusable components, (4) implementation plan — then implements only that milestone. Every implementation obeys CLAUDE.md and the standards in `docs/CODING-STANDARDS.md`: Java 21, Clean Architecture, DDD, SOLID, database-per-service, constructor injection, immutable domain models, DTO separation, RFC-7807, OpenAPI, validation, correlation IDs, RBAC, audit logging, and comprehensive tests. No API, service, or schema outside `docs/ARCHITECTURE.md`.
