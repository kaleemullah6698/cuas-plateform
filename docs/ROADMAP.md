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

**All of B1–B6 are now CLOSED.** B6 was retired by M5.

| # | Blocker | Impact | Retired in | Status |
|---|---|---|---|---|
| B1 | **`contracts/proto/*` empty.** `detection.proto`, `inference.proto`, event protos unwritten. | Nothing downstream compiles. The highest-value gap in the repo. | M1 | ✅ **Closed** — both protos authored, generating and round-tripping in Java/Python/TS |
| B2 | **Toolchain unverified.** No JDK/Maven on the generating machine; `mvn verify` and `npm ci` never run. | Build may fail on POM coordinates or dependency versions on first attempt. | M0 | ✅ **Closed** — JDK 21 + Maven 3.9.9; full reactor `clean install` green (16 modules, 38 tests) |
| B3 | **`platform-commons` empty.** Every service depends on it. | Services can't share cross-cutting concerns; duplication starts immediately if skipped. | M0 | ✅ **Closed** — implemented, 23/23 tests |
| B4 | **Kafka topics not declared.** `create-topics.sh` is a placeholder; auto-create is off by design. | Producers/consumers fail at runtime. | M1 | ✅ **Closed** — 24 topics created live, configs verified, produce/consume round-trip passes |
| B5 | **DB init absent.** `init-databases.sh` is a placeholder. | Services can't connect to their schema. | M0 | ✅ **Closed** — 10 databases created live on PostgreSQL 17, per-database connections verified |
| B6 | **`track-correlation` is stateful, sector-partitioned** (§4.1.5) — the hardest service. | Highest design risk; must not be rushed. | M5, deliberately isolated | ✅ **Closed** — M5 complete; sector assignment, Kalman state, full lifecycle, merge, Redis hot state and the gRPC stream all built and verified |

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

**M3 — Edge (gateway)** — ✅ **Complete** — 80 tests
Objective: single authenticated ingress. Services: `api-gateway`. Deliverables: JWKS-cached JWT validation, permission pre-check, routing, §7.8 rate limiting, correlation-ID injection. Dependencies: M2. Expected output: authenticated requests reach a stub route; unauthenticated rejected; rate limits enforced. Testing: `@WebMvcTest` filters; rate-limit integration test against Redis; a token-revocation propagation test.

**M3 complete.** `mvn clean verify`: 16 modules, **256 tests**, 0 failures.

**Delivered:** servlet-stack Spring Cloud Gateway MVC (not the reactive gateway — `platform-commons`
ships `jakarta.servlet` filters and a servlet resource-server converter, and a reactive edge would
fork every shared cross-cutting concern); JWKS-cached RS256 validation with issuer, audience and
`jti`-denylist checks; the §7.4 pre-check as a *refuse-only* layer that forwards routes it has no
rule for; §7.8 token buckets as an atomic Redis Lua script; correlation-ID propagation across the
proxy hop; RFC-7807 rejections rendered inside the filter chain, where a `@RestControllerAdvice`
cannot reach; sampled `platform.gateway.access.v1` and always-published
`platform.gateway.ratelimit.v1`; the §4.1.1 `/health` and `/health/ready` probes; OpenAPI for the
gateway's own surface.

**Deliberate omissions, both because the information does not exist at the edge yet:** the
`/ingest/detections` per-sensor limit is keyed on a sensor id that arrives with M4, and the
WebSocket connection cap is a concurrency gate belonging to the M8 fan-out. Declaring a rule that
silently matches nothing would be worse than declaring none.

**Two defects found and fixed during M3, both pre-dating it:**
- **The fail-closed audit publisher was never wired.** `KafkaAuditPublisher` was written in M2.1 but
  no bean definition existed anywhere. auth-service and user-service both inject `AuditPublisher`,
  so **neither could have started**; the gap stayed invisible because every test supplies a mock.
  Registered in `CommonsAutoConfiguration`, conditional on a `KafkaTemplate` and on no service
  supplying its own, with four context tests pinning it.
- **Development Redis ran unauthenticated**, diverging from §8.2. It now holds the gateway's
  rate-limit buckets and revocation denylist, so anyone able to reach it could refill a bucket or
  clear a revocation. `--requirepass` added, with `.env.example` and `verify-infra.sh` updated.

**One defect found and fixed in new code:** the permission pre-check answered `403` to
unauthenticated callers, because `AnonymousAuthenticationToken.isAuthenticated()` returns `true`.
A `403` tells an anonymous caller the route exists and that authenticating would not have helped.
Now resolved with `AuthenticationTrustResolver`, and the same correction applied to the two filters
that read the subject.

**Documented amendment:** §5.2 gained `platform.gateway.access.v1` and
`platform.gateway.ratelimit.v1`, which §4.1.1 requires the gateway to publish but the catalogue
omitted — the same gap, and the same treatment, as `platform.user.deactivated.v1` in M2. Both added
to `create-topics.sh` (catalogue now 26 topics: 20 primary + 6 DLQ) and given schemas in
`contracts/proto/gateway.proto`.

**Decision recorded rather than assumed:** §7.8 specifies the limits but not the behaviour when
Redis is unreachable. The gateway **fails closed** (503). Failing open would let anyone able to
disturb Redis switch the rate limiter off and revive a revoked token; §4.1 already makes Redis the
gateway's datastore, so an unreachable Redis is an unready gateway. Recorded here and in the
gateway README because it is a real availability trade, not an obvious default.

**M4 — Ingestion** — ✅ **Complete** (M4.0 + M4.1 + M4.2)
Objective: heterogeneous sensor input → canonical `DetectionRecord` on Kafka. Services: `sensor-ingestion`, `sensor-simulator`. Deliverables: REST + gRPC intake, per-modality normalisation, UTC + drift correction, dedup by `(sensorId, sensorSeq)`, health telemetry, dataset replay. Dependencies: M1. Expected output: replayed dataset traffic emerges on `sensor.detection.normalized.v1`. Testing: normalisation unit tests per modality; a dedup test; a Testcontainers Kafka round-trip; a replay end-to-end and load test.

Every ROADMAP deliverable is done and the expected output — replayed dataset traffic emerging on `sensor.detection.normalized.v1` — is demonstrated by a Testcontainers test. **M5 can begin:** it consumes `sensor.detection.normalized.v1`, which is produced and stable. The peripheral follow-ups surfaced during design (the `platform.config.sensor.updated.v1` consumer, the `sensor.ingestion.error.v1` publisher, the §7.8 per-sensor gateway limit, DLQ routing, and the §5.6 lowest-quality-first drop) are operational hardening that does not affect that output and does not block M5.

**M4.0 — Contracts & doc amendments** — ✅ **Complete** — no production code
The architecture-review gate for M4. Applied the reviewed-and-approved decisions to the source of
truth before any service code:
- **Pipeline direction (Q1).** `sensor-ingestion` now *consumes* `sensor.detection.raw.v1` and
  publishes `sensor.detection.normalized.v1`. §4.1.4 previously listed `raw` under *Publishes*
  only; it now appears on both lines with a note — the intake adapters (REST/gRPC) publish `raw`,
  a separate consumer normalises it. A sensor adapter, including the simulator, reaches `raw` only
  through the intake APIs, never Kafka directly.
- **Missing consumed topic (Q2).** Added `platform.config.sensor.updated.v1` to the §5.2 catalogue
  and `create-topics.sh` (key `namespace`, ∞ retention, compact — mirroring `platform.config.updated.v1`).
  Same just-in-time precedent as `platform.user.deactivated.v1` and the gateway topics; the sibling
  `policy`/`ai` config sub-topics stay deferred to their consumers (M6–M8).
- **gRPC + health contract (Q3, Q6).** New `contracts/proto/ingestion.proto`
  (`pk.nust.cuas.ingestion.v1`): `DetectionIngestService.StreamDetections(stream DetectionRecord)
  → stream IngestAck`, plus a minimal `SensorHealth`. `IngestAck` carries only `sensor_seq` +
  `accepted`; rejection reasons travel on `sensor.ingestion.error.v1`. Documented as §5.3.1.
  Service named `DetectionIngestService` (not `DetectionIngest`) to satisfy buf `SERVICE_SUFFIX`
  without weakening the ruleset repo-wide.
- **No RBAC change (Q8).** §7.4 untouched — sensors are not users; sensor authentication is an
  infrastructure concern (mTLS / simulator mode).

Verified: `buf lint` clean, `buf breaking` clean (additive — no topic version bump), `buf generate`
regenerated Java/Python/TS bindings; `mvn clean verify` — 16 modules, **256 tests**, 0 failures.
No `sensor-ingestion` code written; M4.1 awaits review.

**M4.1 — Sensor Ingestion Service** — ✅ **Complete** — 41 tests
The service itself, built Clean/hexagonal (§4.3) with a framework-free domain. Delivered: the REST
batch intake (`POST /ingest/detections`, canonical `DetectionRecord` JSON, per-record rejection) and
the bidirectional gRPC `StreamDetections` intake, both publishing verbatim to
`sensor.detection.raw.v1`; a raw consumer that validates, de-duplicates (Redis window,
`seenBefore`/`markSeen`), corrects the clock (per-sensor EWMA drift, `sensor_time` preserved),
assigns a UUIDv7 identity and authoritative `ingest_time`, and publishes
`sensor.detection.normalized.v1`; the `sensordb` schema (`sensors`, `sensor_health`,
`ingestion_stats`) via Flyway; scheduled per-sensor health telemetry on `sensor.health.v1`;
correlation-ID propagation across the raw hop; RFC-7807 errors and OpenAPI.

**Decisions worth defending:** publish-then-mark de-duplication makes the pipeline at-least-once so a
publish failure never loses a detection (§5.6), at the cost of a rare republish a mid-publish crash
could cause, which track-correlation absorbs (§4.1.5); the intake carries no RBAC because sensors
are not users (M8 decision), authentication being an infrastructure concern; the canonical schema is
accepted directly rather than mirrored into a DTO tree, avoiding a second source of truth for §5.3.

**Defect found and fixed:** the EWMA converges to one millisecond below the true offset because the
estimate is rounded to whole milliseconds each step (a stable rounding fixed point); this was a
wrong test expectation, corrected — the rounding is intended, drift being whole-millisecond.

Verified: `mvn clean verify` — 16 modules, **297 tests** (+41), 0 failures; the 3 integration tests
run the full pipeline against real PostgreSQL, Kafka, and Redis (Testcontainers).

**M4.2 — Replay, simulator, and load** — ✅ **Complete** — the rest of M4 that needs no contract change
- **Dataset replay** (`POST /ingest/replay`): reads newline-delimited `DetectionRecord` JSON and
  feeds each detection through the same intake a live request uses — no shortcut into Kafka or the
  database. Registered only when `ingestion.replay.enabled` is true (off by default); a disabled
  instance returns 404 because the `@ConditionalOnProperty` controller is never created. The batch
  and replay paths share one `DetectionJsonParser`, so they agree on what valid JSON is.
- **`sensor-simulator`** (new module): a headless development sensor adapter that generates synthetic
  multi-modality detections and drives them into ingestion over the **real** REST and gRPC endpoints,
  never writing to Kafka or the database. Transport (`REST`/`GRPC`/`BOTH`), sensor count, and volume
  are configurable; a run fires only when `simulator.run-on-startup` is set.
- **Health telemetry** exercised end to end: a reporting sensor's health is emitted on
  `sensor.health.v1` as `ONLINE`.
- **Defect found and fixed (platform-wide):** `BaseExceptionHandler`'s catch-all turned Spring's
  `NoResourceFoundException` into a 500, so every unknown path in every service returned 500 instead
  of 404. Added a dedicated handler; unmatched routes now correctly 404. This is what makes the
  disabled replay endpoint report 404 as intended.

Verified: `mvn clean verify` — **17 modules, 321 tests** (+24), 0 failures. Integration coverage adds
a replay round-trip, a **2,000-detection load test** (all normalised through the real pipeline), and
a health-telemetry assertion, all on Testcontainers PostgreSQL + Kafka + Redis. The simulator's REST
client is tested against a mock HTTP server and its gRPC client against an in-process gRPC server, so
both are verified against the real wire contract.

**M4 is complete.** The peripheral follow-ups (config-topic consumer, error-topic publisher, gateway
per-sensor limit, DLQ, §5.6 drop-order) are hardening that does not affect the normalised output and
does not block M5.

**M5 — Correlation** — ✅ **Complete**
Objective: detections → tracks. Services: `track-correlation` (the stateful, sector-partitioned service — isolated deliberately). Deliverables: spatio-temporal association, Kalman state, lifecycle (tentative→confirmed→coasting→lost), CPA to assets, gRPC Infer call to orchestrator (circuit-broken). Dependencies: M4, and M6's contract (calls orchestrator). Expected output: `track.created/updated/lost` events from replayed detections. Testing: heavy domain unit tests on association and Kalman; a lifecycle-transition suite; integration test consuming real detection events.

**M5.1 — Track Correlation Foundation** — ✅ **Complete** — 31 tests
The service, built Clean/hexagonal with a framework-free domain. Delivered: consume
`sensor.detection.normalized.v1`; a spatio-temporal association pipeline (sector assignment →
space-and-time gate → nearest-neighbour); track creation (tentative) and update, with confirmation
on a detection-count threshold (tentative → confirmed); the `trackdb` schema (`tracks`,
`track_states`, `track_detections`, `track_lifecycle_events`) with the full aggregate history
persisted; `track.created.v1` / `track.updated.v1` publication; the `GET /tracks`, `/{id}`,
`/{id}/history` read API guarded by `track:read`; correlation-ID propagation; RFC-7807; OpenAPI.

**Contract added (§5.3.2):** `track.proto` (`Track`, `TrackStatus`) — §4.1.5 named the track events
but never defined their fields. The minimum was defined and ARCHITECTURE.md §5.3.2 amended. The
lifecycle enum declares `COASTING`/`LOST` as reserved (emitted in M5.2); position reuses
`detection.v1.GeoPosition`. Topics were already catalogued in §5.2, so no §5.2 change.

**Decisions worth defending:** the domain is proto-free (mapped at a boundary); the aggregate is
immutable (associate returns a new `Track` plus its lifecycle event); the consumer runs
single-threaded so a read-modify-write on a track is a plain transaction; correlation is idempotent
(unique `(track_id, detection_id)` + a pre-check) so redelivery is safe; persist-before-publish
in-transaction so a publish failure reprocesses rather than orphans a track. A shared `Uuidv7Generator`
was promoted to `platform-commons` (both ingestion and tracks assign UUIDv7 ids) rather than
duplicated.

Verified: `mvn clean verify` — **17 modules, 352 tests** (+31), 0 failures; the integration tests run
the full consume → associate → persist → publish pipeline and the secured read API against real
PostgreSQL + Kafka (Testcontainers).

**Deliberately deferred (later M5 sub-milestones):** Kalman state estimation, trajectory prediction,
CPA, cross-sensor fusion, coasting/lost transitions, the AI-orchestrator gRPC call, the Redis
hot-state cache and concurrent per-sector processing, and the gRPC `SubscribeTracks` stream.

**M5.2 — Kalman state & miss-timeout lifecycle** — ✅ **Complete** — 58 tests (+27)
The motion model and the second half of the lifecycle. Delivered: a constant-acceleration,
three-state (position, velocity, acceleration) Kalman estimator per axis of a local ENU frame
anchored at the track's first position; **predict-then-gate** association — each candidate is
offered at the position its own filter predicts for the detection's time, not its last fix;
confirmed → `COASTING` (predicted forward, republished on `track.updated.v1`) and open → `LOST`
(terminal, published on `track.lost.v1`) on miss timeouts; revival of a coasting track by a
detection; `V2__add_kalman_state.sql` persisting `observed_at` + the serialised filter state, so the
filter survives restarts rather than starting cold.

**The separate time gate was removed, not lost.** Association is now purely spatial and the temporal
bound is applied by the candidate query, which never offers a track older than the lost timeout.
That keeps `AssociationPolicy` a pure function and removes a gate that the motion model made
redundant.

**Defect found and fixed during this sub-milestone.** A track carries two clocks that diverge while
coasting: `lastDetectionAt` (what the miss timeouts count from) and `estimate().observedAt()` (what
the filter state is valid for). `associate()`, `coast()`, and the candidate-prediction loop all
measured the Kalman `dt` from `lastDetectionAt`, which **re-advanced a coasting track's filter over
an interval it had already covered** — a track coasting 9 s then re-acquired received ~19 s of
prediction for 10 s of elapsed time, corrupting position, velocity and acceleration on every
revival and skewing the gate that decides whether the revival happens at all. It was invisible
because the existing revival test asserted only on `status`, never on kinematics. Fixed in all
three places and pinned by a domain test that coasts a moving track to T+20, then feeds a
measurement at exactly T+20 at exactly the predicted position — a zero-length step onto a zero
innovation, so the estimate must not move.

**Two defects in the interrupted state of this sub-milestone**, both build-breaking and both fixed:
a missing `TestPropertySource` import, and a `TrackLifecycleIntegrationTest` that the pipeline
test's comment referenced but which had never been written.

Verified: `mvn clean verify` — **17 modules, 379 tests** (+27), 0 failures. The new
`TrackLifecycleIntegrationTest` drives coast → lost and the revival against real PostgreSQL + Kafka
(Testcontainers).

**M5.3 — Contracts & doc amendments for the rest of M5** — ✅ **Complete** — no production code
The architecture-review gate for the remainder of M5, following the M4.0 precedent: the source of
truth is amended *before* the service code that depends on it.
- **§5.2 catalogue (two-way inconsistency).** §4.1.5 listed `track.merged.v1` and
  `track.classified.v1` under *Publishes*, but neither appeared in the §5.2 topic catalogue or in
  `create-topics.sh`. Both are now catalogued (key `trackId`, 6 partitions, 30 d, delete) and
  created. The script's counts are computed from the arrays, so no hardcoded total moved.
- **§5.3.2 track schema.** §5.3.2 promised the estimator state, trajectory prediction and CPA
  fields "added in M5.2" but never defined them. Now defined: `KinematicEstimate` (vertical speed,
  acceleration, 1σ position and velocity uncertainty), `PredictedPoint`, `ClosestPointOfApproach`,
  `merged_into_track_id`, and `TRACK_STATUS_MERGED`. **`buf lint` clean, `buf breaking` clean** —
  every change is additive and therefore a minor version under §5.5, so no topic version bumps.
- **`observed_at` / `last_seen` documented as two clocks.** The M5.1 text described `observed_at` as
  the latest detection's ingest time, which is what `last_seen` now carries. No consumer existed, so
  the correction costs nothing — but it is the contract-level statement of the defect above.
- **The merge rule, previously unspecified.** "Cross-sensor duplicate elimination" was named once in
  §4.1.5 and defined nowhere. Now specified in §4.1.5: both tracks `CONFIRMED`, same sector,
  **disjoint sensor sets**, estimates within the merge gate for **N consecutive sweeps**; the older
  track (UUIDv7 ordering) survives. The N-sweep requirement exists because a false merge makes one
  of two real threats disappear, which is the more damaging error than a missed merge.
- **Protected assets sourced through a port.** §4.1.12 assigns asset geometry to the Configuration
  Service, which does not exist until M8. §4.1.5 now records that the registry is a domain port
  (`ProtectedAssetRegistry`) with a configuration-backed adapter today and a config-service-backed
  adapter at M8 — an infrastructure swap with no domain, CPA, or contract change. Database-per-service
  is never violated at any stage. Same just-in-time precedent as M4.0's sensor registry.

**Technical debt recorded, not silently fixed — §5.7 delivery semantics.** §5.7 specifies
*effectively-once* for `Ingestion → normalized` and `Correlation → track events` via "Kafka
transactions (`read_committed`); offsets committed inside the same transaction as output". **No
Kafka transactions exist anywhere in the platform** — a repo-wide search for `isolation`,
`transactionIdPrefix` and `KafkaTransactionManager` returns nothing. Both services instead use an
idempotent producer plus a dedup guard, which closes the duplicate-on-replay path but leaves the
dual-write window open: a Kafka publish that succeeds followed by a database rollback emits a track
event with no track behind it. This dates from M4, spans two services and two milestones, and is a
change to a delivery guarantee rather than a bug fix — scheduled for the M8 hardening pass.

**Still open in M5:** M5.4 CPA + trajectory prediction, M5.5 cross-sensor merge, M5.6 Redis
hot-state, M5.7 gRPC `SubscribeTracks`. **Deferred to M6 with cause:** the circuit-broken gRPC
`Infer` call to `ai-orchestrator` and the `ai.inference.completed.v1` consumer that produces
`track.classified.v1` — `ai-orchestrator` is pure scaffold until M6, and this milestone's own
dependency line already reads "M4, **and M6's contract**".

**M5.4 — Trajectory prediction & CPA** — ✅ **Complete** — 76 tests (+18)
The forward-looking half of §4.1.5, built on the M5.3 contract. Delivered: `TrajectoryPredictor` and
`CpaCalculator` (both named in §4.3), a `ProtectedAssetRegistry` domain port with its
configuration-backed adapter, `TrackOutlookCalculator` composing the two, and the estimate,
trajectory and CPA published on every track event.

**Decisions worth defending:**
- **The outlook is derived, never persisted.** `TrackOutlook` is recomputed from the filter on every
  publication. Storing it would create a staler second copy of something derivable in microseconds,
  and would need invalidating whenever the asset registry changed.
- **Only confirmed and coasting tracks get one.** A `TENTATIVE` track may be a sensor artefact whose
  filter has seen too few measurements for its velocity to mean anything; a confident-looking
  trajectory and a CPA countdown on the operator's screen for something that may not exist is worse
  than showing nothing. A `LOST` track is not going anywhere. Coasting tracks *do* get one — that is
  precisely when an operator wants to know where something is heading.
- **CPA is closed-form, not a scan of the trajectory.** It solves `t = -(r·v)/|v|²` directly. Scanning
  the predicted points for the nearest one would be bounded by the prediction horizon and quantised
  by its step, and would report a CPA at the horizon's edge for anything approaching more slowly.
- **CPA is horizontal only.** Folding altitude in would make a high overflight of an asset read as
  distant when the ground track passes directly over it.
- **"Nearest asset" means the one it will get closest to**, not the one it is closest to now — which
  is the question §6.3.4 threat scoring is actually asking. Pinned by a test where a track sits beside
  one asset while flying straight at another.
- **Each predicted point is computed by one advance from the present**, not by chaining the previous
  prediction. Chaining `i` single steps accumulates process noise `i` times and overstates how fast
  confidence decays; one advance of `i·step` accumulates it once for that interval.
- **A receding track reports `closing = false`**, not a negative time-to-CPA, and a missing asset
  registry yields no CPA rather than a fabricated default.

Verified: `mvn clean verify` — **17 modules, 397 tests** (+18), 0 failures. `TrackLifecycleIntegrationTest`
now configures a protected asset and asserts the coasting track reaches Kafka carrying a predicted
trajectory with monotonically growing uncertainty, the named CPA, and a positive position
uncertainty — the whole path from filter to wire.

**M5.5 — Cross-sensor duplicate elimination** — ✅ **Complete**
The merge rule M5.3 specified, now built. Delivered: `TrackMergePolicy`, the `MergeCandidate`
evidence value, the `track_merge_candidates` table, a merge sweep on its own cadence,
`Track.mergeInto`, the `MERGED` terminal status with `merged_into_track_id`, and `track.merged.v1`.

**Decisions worth defending:**
- **`MERGED` is a distinct terminal status, not reused `LOST`.** The two mean opposite things
  downstream: a lost track is an object the system stopped seeing, a merged one is an object still
  being tracked under another id. Collapsing them would tell an operator a live threat had gone away.
- **The consecutive-sweep requirement is the whole safeguard, and it is durable.** The evidence lives
  in a table rather than in memory, because a service restart mid-accumulation would otherwise either
  lose the evidence or — worse, had it been cached — resume from a count no longer backed by
  observation. A false merge makes one of two real threats disappear; that asymmetry justifies the
  extra table.
- **The pair is keyed lower-id-first, enforced by a database `CHECK`.** Without it `(a,b)` and
  `(b,a)` could accumulate separately and a pair would merge on half the intended evidence.
- **Disjoint sensor sets are required.** One sensor reporting an object twice is a duplicate
  detection, which association already handles. Two tracks sharing a sensor are far more likely to be
  two real objects that sensor is resolving separately — the formation case.
- **Coasting tracks are excluded.** Their positions are predicted rather than observed, and merging
  on a prediction would let two tracks collapse because neither is being measured.
- **The survivor is the lower UUIDv7.** No timestamp comparison, no tie to break, and every instance
  decides identically from the ids alone. It is also the id consumers have known longest, so it
  minimises the re-keying a merge forces on them.
- **Evidence is voided whenever a track leaves `CONFIRMED`** — on coast, loss, or merge — so stale
  evidence can never make a later, unrelated pairing merge early.
- **Both tracks are re-read inside the merge transaction**, not trusted from the sweep's snapshot, and
  a track merged earlier in a sweep is excluded from the rest of it — otherwise three mutually-close
  tracks could produce a second merge into a track that had just become terminal.

`TrackMergeIntegrationTest` proves it end to end: two sensors 80 m apart open separate tracks (the
association gate is shrunk below the merge gate, which is what makes the scenario reachable — with
the production gate they would simply associate), the pair holds for three sweeps, the younger is
absorbed, `track.merged.v1` carries both ids, and the lifecycle transition and evidence cleanup are
both persisted. A second test proves two tracks fed by the *same* sensor never merge however close
they fly.

**M5.6 — Redis hot in-flight state** — ✅ **Complete**
§4.1.5's "Redis holds hot in-flight state", built as §4.3's `RedisTrackStateStore`. One Redis hash
per sector, fields keyed by track id, in front of the per-detection candidate query — the pipeline's
highest-volume hop, inside §5.8's 80 ms correlation budget. Written through on create, association,
and coast; evicted on loss and merge.

**Decisions worth defending:**
- **The cache fails open, and the port says so.** A miss, an unreadable entry, and an unreachable
  Redis are all defined as "miss", and the caller falls back to PostgreSQL. This is the deliberate
  opposite of the gateway's fail-closed rule (§7.8): there an unreachable Redis means a security
  control is not being enforced, so the request must be refused; here it means a query is slower.
  Refusing to correlate because a cache is down would turn a degraded system into an undetected
  drone, which §5.6's governing principle forbids.
- **PostgreSQL remains the source of truth.** Nothing is ever read from the cache that is not also in
  the database, so the cache can be flushed at any moment with no loss.
- **A hash per sector, not a serialised list.** A detection changes one track; a list would make
  every write an O(sector) rewrite and a lost-update race between the consumer and the two sweeps.
- **The TTL is the lost timeout.** Past it a track could not have been an association candidate
  anyway, so no stale entry outlives its usefulness even if an evict is lost — the safety net that
  keeps a cache bug from becoming a correctness bug.
- **A single unreadable entry misses the whole sector**, rather than returning a short list. A
  partial candidate set would let a detection open a duplicate track rather than associate to the one
  it could not read — silently worse than a miss.
- **The no-op store is a supported configuration, not a test double.** `correlation.hot-state.enabled=false`
  runs the service with no Redis at all. The port is written so a permanent miss is correct, and that
  claim is only credible if something exercises it — the integration tests do, running the whole
  suite against the database path.
- **One Kalman codec, not two.** `KalmanStateCodec` was widened within the infrastructure layer
  rather than duplicated for Redis: a divergence between two implementations would surface as a track
  whose filter resumed differently depending on where it was read from.

`RedisTrackStateStoreIntegrationTest` runs against a real Redis container — a full Kalman state
round-trips bit-identically, the merged-survivor link survives, evict removes one field, the TTL is
applied, and a corrupt entry reads as a miss. Three unit tests cover the fallback contract: a hit
avoids the database, a miss falls back and reseeds, and a terminal track left stale in the cache is
never offered as a candidate.

**Defect found by that round-trip test — `Axis.equals` compared arrays by reference.** `Axis` is a
record with a `double[][]` covariance component, and a record's generated `equals` compares an array
component by *identity*. Two axes carrying identical numbers were therefore unequal, so every round
trip through the database or the cache looked like a different state — and any assertion that a
filter resumed where it left off was vacuous. Fixed with an explicit `equals`/`hashCode`/`toString`
using `Arrays.deepEquals`/`deepHashCode`, and pinned by `AxisTest`. Latent since M5.2; it had caused
no production misbehaviour because nothing compared two `Axis` values, but it would have silently
defeated exactly the checks written to catch a filter-resume bug.

**M5.7 — gRPC `SubscribeTracks`** — ✅ **Complete**
The streaming API of §4.1.5. `contracts/proto/track.proto` gains `TrackService.SubscribeTracks`,
`SubscribeTracksRequest` (optional sector filter), `TrackEvent` and `TrackEventType` — additive, so
`buf breaking` stays clean. Served on port 9084 alongside HTTP, wired directly against grpc-java as
sensor-ingestion and user-service already do.

**Decisions worth defending:**
- **Fed in process, not from Kafka.** An instance owns its sectors exclusively, so every change to a
  track it owns happens inside it. Feeding the stream from the broker would add a round trip to a
  latency budget (§5.8) already spent on inference, and give every subscriber a consumer group.
- **Kafka first, and only its failure propagates.** `BroadcastingTrackEventPublisher` publishes to
  Kafka — whose failure rolls the transaction back — then broadcasts, whose failure is logged and
  swallowed. The topics are the durable record; the stream is a live feed with no replay. Failing
  correlation over one slow subscriber would let a consumer stop the tracker.
- **A not-ready subscriber's event is dropped, never buffered.** Unbounded queueing would let one
  stalled consumer grow heap inside the service that must not stop tracking drones (§5.6).
- **The mapper moved to `common/`.** Both the Kafka producer (`infrastructure/`) and the stream
  (`api/`) must map domain → contract, and CLAUDE.md §2 forbids either package from importing the
  other. Duplicating would let the two surfaces describe the same track differently.
- **Authorisation is transport-level, per §7.2.** `Service ↔ Service` is mTLS with SPIFFE-style
  workload identity — the same protection the existing gRPC surfaces rely on, and why none carries
  `@PreAuthorize`. `track:read` (§7.4) is a *user* permission the gateway enforces before fanning out
  over its WebSocket (§3.5). Accepting a user token on this internal stream would create a second,
  weaker path to data the gateway already guards. Recorded here because it is a real security
  decision, not an omission.

`TrackStreamGrpcServiceTest` covers all four event types and their tags, the sector filter, an empty
filter meaning all sectors, a stalled subscriber being skipped without starving the others, a failed
write detaching its subscriber, and cancel-plus-close detaching exactly once.

---

Verified: `mvn clean verify` — **17 modules, 448 tests, 0 failures**.

**Known test-harness noise, not a defect:** after the track-correlation suite passes, surefire logs
"going to kill self fork JVM" — the timeout and merge schedulers keep firing while Testcontainers
has already stopped PostgreSQL, so the JVM takes over 30 s to exit *after* the tests have passed and
exit code 0 has been issued. It costs build time and log noise, nothing else. Left as-is rather than
suppressed, because the honest fix is scheduler-lifecycle ordering at shutdown and that belongs with
the M8 hardening pass.

**M5 is complete.** Objective met: detections become tracks, and `track.created/updated/lost/merged`
are produced from replayed detections. Association, Kalman state, the full lifecycle, trajectory
prediction, CPA, cross-sensor merge, Redis hot state and the gRPC stream are all built and verified.
Two items are deferred to M6 with cause — the circuit-broken `Infer` call to `ai-orchestrator` and
the `ai.inference.completed.v1` consumer behind `track.classified.v1` — because that service does not
exist until M6, which is what M5's own dependency line ("M4, **and M6's contract**") anticipated.
**B6, the open design risk since Phase 1, is closed.**

**M6 — AI boundary** — 🚧 **In progress**
Objective: the Track-A/Track-B decoupling, working with zero models. Services: `ai-orchestrator`, `mock-inference-provider`. Deliverables: modality routing, per-service timeout + Resilience4j breaker + bulkhead, content-hash cache, canary split, mock fallback publishing `ai.service.degraded.v1`; deterministic latency-realistic failure-injecting mock badged `0.0.0-mock`. Dependencies: M1. Expected output: `ai.inference.completed.v1` produced from mock; breaker opens and falls back under injected failure. Testing: the shared conformance suite (pytest) against the mock; breaker/bulkhead unit tests; a fallback integration test.

**Protocol decision, recorded rather than reopened.** §6.1 requires every AI capability to implement
the gRPC `InferenceService`, and §6.2 specifies that contract in full — it has been authored in
`contracts/proto/inference.proto` and compiling since M1. REST would contradict a frozen contract, so
gRPC it is; the reasons still hold (one IDL for messaging and RPC, native deadline propagation, which
is *how* the orchestrator enforces per-service timeouts, streaming for `InferStream`, and mTLS per
§7.2). REST appears only on the orchestrator's own operator surfaces — `POST /inference`,
`GET /ai/services`, `POST /ai/services/{id}/model-version` (§4.1.6) — never on the inference path.

**M6.1 — Orchestrator foundation** — ✅ **Complete** — 42 tests
The model-agnostic core. Delivered: `AiServiceRegistry` and `ModalityRoutingPolicy` (framework-free
domain), the `InferenceProvider` port, `Modality` with its boundary mapper, and startup-validated
`ai.services` configuration. No provider implementations yet — a request routes to a set of
descriptors and stops there.

**Decisions worth defending:**
- **Routing is by modality, never by service identity.** A modality is a fact about the *sensor*; a
  service name would be a fact about a *model*. This is the whole mechanism behind §6.1's guarantee,
  and §6.1 has been amended to record it.
- **`InferenceProvider` takes the descriptor as a parameter**, so one implementation serves every
  registered service. A bean per model would itself be the code change §6.1 forbids.
- **The port lives in `application/`, not `domain/`, and speaks the generated Protobuf types.** §6.2
  *is* the contract and §6.4 requires the mock to be schema-identical so conformance is
  compile-enforced; a private translation layer would let mock and real service drift apart in
  exactly the way the shared conformance suite exists to prevent. `domain/` stays contract-free
  behind `ModalityMapper` (CLAUDE.md §2).
- **A multi-modal service is invoked once per request**, not once per matching modality — otherwise a
  fusion model is called twice for one track: double the GPU load, two competing results for one
  object.
- **An unrecognised modality routes nowhere**, rather than to a default. A confident-looking result
  from a model that never saw that input is worse than no result.
- **Configuration fails at startup**, not at the first inference. A misconfigured AI service that
  only surfaces under load is the failure this system can least afford.
- **Providers throw only on *transport* failure**; an inference that could not produce a result
  returns a status. Conflating them would open a circuit breaker because a model honestly reported
  low confidence.

**The rule is enforced, not merely documented.** `ModelAgnosticismTest` scans the module's source for
the §6.3 service and framework names and fails if any appears, comments included — deliberately a
blunt string check rather than a comment-aware one, since naming a model in prose is the first step
towards branching on it. **It caught a violation on its first run**: a javadoc in `ServiceId` used a
real service name as an illustration. Reworded rather than the check weakened.

Verified: `mvn clean verify` — **17 modules, 490 tests** (+42), 0 failures.

**M6.2 — Mock inference provider** — ✅ **Complete** — 84 orchestrator tests (+42)
§6.4's mock, built as the first-class component it is specified to be. Delivered: `ScenarioLibrary`
(six scenarios, deterministically assigned), `LatencyModel` (lognormal fitted to each service's p95),
`FailureInjector` (timeout / model-error / low-confidence rates), and `MockInferenceProvider`
emitting the generated contract types.

**Decisions worth defending:**
- **It lives in-process, inside the orchestrator, not as a container.** §6.4 specifies it as a Java
  component behind the port, and that is load-bearing: the mock is the fallback an *open circuit
  breaker* falls onto (§6.5), so it must be reachable when the network is exactly what has failed. A
  mock served over the same gRPC transport as the real services could be down for the same reason
  they are.
- **Its bean is unconditional — no `@ConditionalOnProperty`.** It is not a development mode a
  production profile switches off; a profile that could remove it would remove the platform's ability
  to degrade instead of fail.
- **Deterministic, never random.** Every decision derives from a 64-bit FNV-1a seed over track and
  service id. `String.hashCode` was rejected deliberately: track ids are UUIDv7 and share a long
  common prefix, so a weak 32-bit hash clusters consecutive tracks into one scenario and defeats the
  library. A `java.util.Random` would make every injected failure unreproducible — the difference
  between a demonstrable defect and one that "happens sometimes".
- **Lognormal latency, fitted so p95 lands on the configured budget** (`mu = ln(p95) − 1.645·sigma`).
  Real inference latency is clustered near a median with a long right tail; a constant would make
  every p95 dashboard, timeout and budget assertion meaningless, and would make the system look
  better-behaved under mock than it will ever be under a real model. The fit also means a timeout is
  hit *occasionally but not usually* — the condition a breaker needs to be exercised against.
- **Injection rates are drawn from disjoint bands of one value**, so they compose exactly:
  10%+10%+10% is 30% failure, not the 27% independent draws would give.
- **A pinned version is appended, never substituted** — `0.0.0-mock+pinned-1.3.0`. A canary pin must
  not let a mock claim to be a real version.
- **It is always healthy.** A fallback that could itself be unavailable is not a fallback.
- **It serves every registered service and knows none of them**; the descriptor arrives as a
  parameter. A mock that varied by service would have to know what each service models.
- **An injected timeout is returned as a *status*, not thrown.** Providers throw only on transport
  failure; the resilience layer classifies the status. Conflating them would open a breaker because a
  model honestly reported low confidence.

Verified: `mvn clean verify` — **17 modules, 542 tests** (+52), 0 failures.

**M6.3 — Resilience** — ✅ **Complete** — 96 orchestrator tests (+12)
§5.6 and §6.5's protections, per service. Delivered: `ResilientInferenceDispatcher` layering a
bulkhead, a circuit breaker and a time limiter, falling back to the mock and announcing degradation
through a `DegradationListener` port; breaker and bulkhead state exported to Micrometer.

**Decisions worth defending:**
- **The breaker sits *inside* the bulkhead.** A bulkhead rejection means the service is busy, not
  broken, so it must not count towards opening a breaker — reversing the order would let a traffic
  spike take a perfectly healthy service offline. There is a test for exactly that.
- **Only transport failures reach the breaker.** `STATUS_MODEL_ERROR`, `STATUS_LOW_CONFIDENCE` and
  `STATUS_INSUFFICIENT_DATA` are the *model's answer*, not evidence the service is unreachable; they
  pass through and count as breaker successes. Counting them would take a healthy service out of
  rotation for telling the truth, and would make the breaker fire hardest exactly when the airspace
  is most ambiguous. 30 consecutive model errors leave the breaker closed.
- **A response that *reports* a transport failure is lifted into a thrown one.** The mock reports an
  injected timeout as `STATUS_TIMEOUT` rather than throwing, and a gRPC client may surface a
  server-side deadline the same way. Without lifting it, an injected timeout would be recorded as a
  breaker *success* and the breaker could never open under injected failure — which is precisely
  M6's stated expected output.
- **Registries, not `@CircuitBreaker` annotations.** The annotation needs a name at compile time, and
  §6.1's whole point is that service names live only in configuration. Resolving instances by id at
  call time is what keeps a new model a config entry.
- **The deadline runs on a separate (virtual) thread.** A time limit that only fires when the callee
  cooperates is not a time limit. Virtual threads because the orchestrator is IO-bound by design; a
  fixed pool would impose a second, invisible concurrency cap behind the bulkheads that are supposed
  to be the only one.
- **Bulkhead wait is zero.** A saturated service degrades immediately rather than adding queueing
  latency to a request already against a deadline (§5.6).
- **A listener that throws cannot fail the inference it describes** — that would turn a handled
  outage into an unhandled one, the exact opposite of the port's purpose.
- **`InferenceAttempt` is separate from `InferenceOutcome`.** One says how the *model* did, the other
  which *path* answered. Collapsing them would make a fallback indistinguishable from a real result
  at the moment an operator most needs to know the difference (§6.5).
- **`DegradationReason` distinguishes open-breaker, full-bulkhead and timeout.** The remedies differ —
  wait, scale, or investigate — so a single "degraded" would discard the only signal separating them.

**Defect found: `ai.service.degraded.v1` was never catalogued.** It is listed under §4.1.6's
*Publishes*, appears in §6.5's sequence diagram, and §7.6 requires the SIEM to forward it — but it was
absent from the §5.2 topic catalogue and from `create-topics.sh`. With topic auto-creation disabled
by design, publishing to it would have failed at runtime. Added to both (key `serviceId`, 3
partitions, 30 d, delete — matching `ai.inference.failed.v1`, its nearest sibling in criticality).
**This is the third topic of the same family**, after `track.merged.v1` and `track.classified.v1` at
M5.3: a topic named in a service's *Publishes* line but missing from §5.2.

**Sweep performed — the gap is systematic, and now measured.** Cross-checking every §4.1 *Publishes*
and *Consumes* line against the §5.2 catalogue and `create-topics.sh`: the catalogue holds **25
topics**, all 25 are created, and none is created that is not catalogued — but the service inventory
names roughly **40 distinct topics**. The uncatalogued remainder, by owner:

| Owner | Uncatalogued topics |
|---|---|
| `auth-service` | `platform.auth.login.success/.failed/.logout/.token.revoked/.lockout.v1` |
| `user-service` | `platform.user.created/.updated.v1`, `platform.role.assigned/.permissions.changed.v1` |
| `audit-service` | `audit.chain.verified/.broken.v1` |
| `sensor-ingestion` | ~~`sensor.health.degraded/.offline.v1`~~ — **resolved at M7.A**: phantom topics, never produced. §4.1.8/§4.1.9 amended to consume `sensor.health.v1` and filter on status |
| `threat-assessment` (M7) | ~~`threat.recommendation.generated.v1`~~ — **catalogued at M7.A** |
| `alert-service` (M7) | ~~`alert.escalated/.resolved.v1`, `incident.updated/.closed.v1`~~ — **catalogued at M7.A** |
| `notification-service` (M7) | ~~`notification.sent/.failed.v1`~~ — **catalogued at M7.A** |
| `reporting-service` (M8) | `report.generation.started/.generated/.generation.failed.v1` |
| `config-service` (M8) | `platform.config.policy/.ai.updated.v1` |

**No current runtime defect.** Every topic the built services actually publish to —
`platform.audit.v1`, `platform.gateway.access/.ratelimit.v1`, `platform.user.deactivated.v1`,
`sensor.detection.raw/.normalized.v1`, `sensor.health.v1`, `track.created/.updated/.lost/.merged.v1` —
is catalogued and created. The uncatalogued entries are publications not yet implemented in built
services, or belong to services that do not exist until M7/M8.

**Deliberately not fixed here.** Cataloguing them requires choosing a key, partition count, retention
and cleanup policy for each, which is specification the owning milestone must make with knowledge of
its consumers — inventing 20 rows now would be exactly the "do not invent" failure CLAUDE.md §0
guards against. Each is added by its owning milestone, as M4.0, M5.3 and M6.3 each did. The value of
this sweep is that the remaining work is now enumerated rather than discovered one outage at a time.

Verified: `mvn clean verify` — **17 modules, 554 tests** (+12), 0 failures.

**M6.4 — Kafka events & content-addressed cache** — ✅ **Complete** — 135 orchestrator tests (+39)
The pipeline half. Delivered: `InferenceOrchestrator` (route → cache → dispatch → publish),
`ProviderSelector`, the Redis inference cache behind an `InferenceCache` port, and
`InferenceEventKafkaPublisher` producing all four §5.2 topics.

**Decisions worth defending:**
- **The cache is content-addressed, not track-addressed.** Keying by `trackId` is the obvious
  shortcut and is wrong in the direction that matters: a track is a thing that *changes*, so its
  second inference is usually a different question, and answering it from the first would freeze a
  moving drone's classification at whatever it was when first seen. The key is a SHA-256 over the
  detection fingerprints and the options that change what the question means.
- **Request and correlation ids are excluded from the key.** They differ on every call by
  construction; including them would make every key unique and the cache would never hit.
- **SHA-256, not a fast non-cryptographic hash.** A collision here does not merely waste work — it
  returns *one track's classification for another's detections*. That is a wrong answer about the
  airspace, and worth microseconds to make infeasible. Fingerprints are length-prefixed so
  `["ab","c"]` and `["a","bc"]` cannot collide.
- **A pinned model version is part of the key.** A canary asks a *different model* the same question;
  sharing a key would serve the incumbent's answer and make the canary invisible.
- **A cache hit does not emit `ai.inference.requested.v1`.** That topic measures how much inference
  the system actually asks for; counting an answer that never reached a provider would overstate
  demand and hide the cache's value in the one metric that should show it.
- **Degraded results are never cached.** A fallback answer describes the *service's* health at one
  moment, not the input; caching it would keep serving a mock answer for a full TTL after the real
  service recovered.
- **Failures go to `ai.inference.failed.v1`, never `completed`**, so a consumer never has to
  distinguish "scored zero" from "not scored". A degraded call emits **both** `completed` and
  `degraded`: downstream gets a usable result, the operator gets the caveat.
- **Inference topics are keyed by `trackId`, degraded by `serviceId`.** One track's events must stay
  ordered on one partition; a degradation is a statement about a *service* over time.
- **Publication never fails the inference** — the opposite of track-correlation's synchronous
  publish, and deliberately so. There, a lost track event means a track nothing downstream knows
  about. Here the caller already holds its answer; failing the call because telemetry could not be
  written would convert a successful inference into a failed one.
- **`ProviderSelector` keys by mode, not by service id**, so fifty registered services still resolve
  to two provider implementations. A map keyed by id would grow with every model and need editing to
  add one — the code change §6.1 forbids. Completeness is validated at startup against the
  *configured* services, so a mock-only deployment needs no gRPC client present.

**Defect found: the default profile shipped four example services.** `application.yml` registered
the four §6.3 services with `mode: mock`. Every deployment inheriting them — production included —
is the configuration-level version of mistaking a mock for a real model. Moved to
`application-dev.yml` as a worked example; the default registry is now `services: {}`, and
`ModelAgnosticismTest` asserts it stays that way. Found because a test's `@TestPropertySource`
*added* to the map rather than replacing it, and the shipped services turned up in the results.

**Defect found: the orchestrator used `spring-boot-starter`, not `-web`.** `platform-commons`'
`CorrelationIdFilter` needs the servlet API, so every Spring context failed with
`NoClassDefFoundError: jakarta/servlet/Filter`. §4.1.6 gives this service three REST surfaces
anyway, so `-web` is the correct dependency rather than a workaround.

Verified: `mvn clean verify` — **17 modules, 583 tests** (+29), 0 failures.

**M6.5 — gRPC provider, canary and admin API** — ✅ **Complete** — 153 orchestrator tests (+18)
The half that makes a trained model reachable. Delivered: `GrpcInferenceProvider` speaking the §6.2
contract, `CanaryPolicy` splitting by `trackId` hash per §6.6, and `GET /ai/services` +
`POST /ai/services/{id}/model-version` under `ai:configure`.

**Decisions worth defending:**
- **One gRPC provider serves every `mode: grpc` service.** The endpoint comes from the descriptor and
  channels are cached per endpoint. A provider bean per model would be the code change §6.1 forbids
  *and* would mean a newly trained model could not be added without a redeploy — the opposite of what
  §6.4's config switch promises.
- **Channels are cached, not per call.** A gRPC channel holds connections and load-balancer state;
  creating one per inference would add a TCP and TLS handshake to every call and defeat the latency
  budget outright.
- **A deadline is set on the stub as well as by the dispatcher.** The dispatcher's time limiter stops
  the *orchestrator* waiting; the gRPC deadline additionally tells the *server* to stop working.
  Without it a timed-out call leaves a GPU computing a result nobody will read, which under load is
  how one slow service becomes a saturated one.
- **The canary splits by track, not per call.** A track that lands on the canary stays there for
  life. Splitting per request would mean one drone's successive classifications came from two models,
  so a change in its threat level could be the model changing rather than the situation — and an
  operator would have no way to tell which. It also makes the comparison meaningful: two populations
  of *tracks*, not of calls.
- **The service id is part of the canary seed**, so a track is not systematically on every service's
  canary at once. Correlated assignment would make one model's regression look like another's.
- **Only the version fields are mutable at runtime.** §4.1.6 requires the endpoint and §6.6 makes
  rollback a configuration change, so versions must change while running — but an id, its modalities,
  its mode and its endpoint stay fixed. Allowing those over HTTP would let an operator reroute the
  pipeline through an API call.
- **`GET /ai/services` requires `ai:configure` too**, not just the write. The registry discloses every
  AI service's endpoint and health, which is a map of the deployment's internal topology.
- **Health is reported, never thrown.** An admin asking which services are up must get an answer
  about all of them, not a 500 because one is down — the unreachable one is exactly what they were
  looking for.

Verified: `mvn clean verify` — **17 modules, 601 tests** (+18), 0 failures.

**M6.6 — Track A integration** — ✅ **Complete**
The two items M5 deferred with cause, now that the orchestrator exists. Delivered: the orchestrator's
own `Infer` endpoint, track-correlation's circuit-broken `AiOrchestratorGrpcClient` behind a
`TrackClassifier` port, the `ai.inference.completed.v1` consumer, and `track.classified.v1`.

**Decisions worth defending:**
- **The orchestrator implements the same `InferenceService` contract it consumes.** §6.5's sequence
  becomes literal: track-correlation issues `Infer` and the orchestrator fans it out. A second,
  orchestrator-specific RPC would be another contract to maintain and would let the two drift.
- **One response is returned from many services** — the most confident *usable* one. Confidence is
  calibrated and comparable across services by construction (§6.2's `ConfidenceReport`), so this is a
  meaningful ordering rather than an arbitrary pick, and every individual result still reaches Kafka
  for threat-assessment to weigh cross-sensor agreement itself (§6.3.3).
- **Nothing usable returns `INSUFFICIENT_DATA`, not an error.** No model was in a position to judge,
  which is a fact about the input rather than a fault.
- **The client is deadlined *and* circuit-broken**, because the call sits inside the
  detection-processing transaction: every millisecond is one the consumer is not reading the next
  detection. The breaker is what turns a *failing* orchestrator from a full deadline per detection
  into one fast rejection — the difference between a degraded tracker and a stalled one.
- **The port returns empty, never throws.** §5.6: the track is never dropped. An unclassified track
  is still a track, and classification is an enrichment.
- **Classification happens last**, after the track is persisted and announced. An unavailable
  orchestrator must not prevent a track being created.
- **Both the synchronous call and the topic consumer exist**, and they are not redundant. `Infer`
  returns the single best answer so the track is enriched immediately; the topic carries *every*
  service's result, including ones that arrived after the deadline or were produced for a track this
  instance did not ask about. §4.1.5 lists both.
- **A classification for a terminal track is discarded.** Inference is asynchronous, so a result can
  outlive the track it describes; reviving a lost or merged track because a late inference arrived
  would be a real defect.
- **A classification is not broadcast on the gRPC stream.** `TrackEventType` is the lifecycle
  vocabulary and a classification changes no status; widening a closed enum for something the next
  `UPDATED` already carries would be the wrong trade.
- **`model_version` and `device` ride all the way to `track.classified.v1`.** §6.4 requires a mocked
  result to stay distinguishable wherever it surfaces, and a classification that reached the
  operator's screen without them would have lost exactly that.
- **Classification can be disabled** (`correlation.ai.enabled=false`). Not a debugging convenience:
  until an orchestrator is deployed, an instance pointed at nothing would spend a deadline per
  detection discovering it. This makes "no AI boundary" an explicit, zero-cost configuration.

**Contract amended (§5.3.2):** `TrackClassification` added to `track.proto` and set on `Track`.
Additive, so `buf breaking` stays clean and no topic version bumps.

**Defect found: a duplicate top-level key in `application-test.yml`.** A second `correlation:` block
was appended rather than merged into the existing one, making the file unparseable and taking down
all four track-correlation pipeline integration tests with "while constructing a mapping". Merged,
and every YAML file touched in both modules is now validated.

Verified: `mvn clean verify` — **17 modules, 607 tests, 0 failures, 0 skipped**.

---

**M6 is complete.** Objective met: the Track-A/Track-B decoupling works with **zero models**.
`ai.inference.completed.v1` is produced from the mock, the breaker opens and falls back under
injected failure, and a detection flows sensor → track → inference → classification end to end. The
AI boundary is model-agnostic by construction and enforced by `ModelAgnosticismTest`. Integrating a
trained model remains a configuration change: `mode: grpc` plus an `endpoint`.

**Deferred from M6, with cause:**
- **The pytest conformance suite** (`ai-services/*/tests/test_contract.py`) stays a scaffold. It is a
  Track-B artifact that runs against a *service*, and no Python inference service exists yet; the
  Java mock's conformance is compile-enforced instead, which is the guarantee §6.4 actually asks for
  at this stage.
- **`InferBatch` and `InferStream`** (§6.2) are unimplemented on both sides. Nothing calls them:
  track-correlation infers one track at a time. Building them now would be speculative surface.
- **`platform.config.ai.updated.v1`** — §4.1.6 lists it as consumed, but config-service does not
  exist until M8; the admin API is the interim source of runtime version changes, exactly as the
  protected-asset registry is for track-correlation.
- **mTLS material** (§7.2) is a deployment concern; both gRPC surfaces bind plaintext in development
  and the platform's certificate configuration terminates them in production.

**M7 — Assessment & alerting** — 🚧 **In progress**
Objective: tracks + inference → scored threats → alerts → incidents → notifications. Services: `threat-assessment`, `alert-service`, `notification-service`. Deliverables: composite scoring (rules-dominant, explainable, §6.3.4), Low/Med/High/Critical, advisory recommendations from the **closed** vocabulary; alert lifecycle, escalation timers, incidents, evidence bundles; multi-channel delivery. Dependencies: M5, M6. Expected output: a replayed intrusion drives an end-to-end alert with a full decision chain. Testing: scoring rule unit tests; alert dedup + escalation integration tests; an idempotency test on threat→alert.

M7 is delivered in four groups: **A** contracts and architecture amendments (no service code), then one group per service — **B** `threat-assessment`, **C** `alert-service`, **D** `notification-service` plus the end-to-end replay.

**M7.A — Contracts & architecture amendments** — ✅ **Complete** — 40 contracts tests (+25)

No service code. Three proto files authored, seven topics catalogued, five architecture sections
amended. `buf lint` clean, `buf breaking` clean against `main` (three new files — nothing additive
to break), `mvn -pl contracts -am verify` green.

**Authored:** `threat.proto` (§5.3.3), `alert.proto` (§5.3.4), `notification.proto` (§5.3.5).

**Four specification gaps closed — each was a genuine blocker, not a preference:**

1. **The Low/Medium/High thresholds did not exist.** §2.4.2 fixes only `CRITICAL ≥ 0.80`; the other
   three boundaries were absent, so a four-level classification was unimplementable. §6.3.4 amended
   with `LOW < 0.30`, `MEDIUM 0.30–0.55`, `HIGH 0.55–0.80`, chosen to reproduce §2.4.2's own rendered
   rows (0.853→CRIT, 0.74/0.71→HIGH, 0.58/0.52→MED, 0.31→LOW) rather than invented. The §2.4.2
   weights (0.40/0.25/0.20/0.10/0.05) are promoted from a wireframe example to normative defaults.
   All of it is policy data, not constants — retuning is a `policy_versions` row with an audit trail.

2. **`sensor.health.degraded.v1` and `sensor.health.offline.v1` do not exist.** §4.1.8 and §4.1.9
   named them as consumed, but §5.2 never catalogued them and §5.3.1 produces a single
   `sensor.health.v1` carrying a `SensorHealth.status`. Both rows amended to consume the real topic
   and filter. **No M4 code was touched** — the defect was in the document, and splitting liveness
   across a topic per status would have given a sensor's transitions no ordering between them.

3. **Seven M7-published topics were missing from §5.2.** The fourth occurrence of the pattern this
   roadmap has been tracking since M4.0 (after `track.merged.v1`, `track.classified.v1` and
   `ai.service.degraded.v1`): a topic in a service's *Publishes* line with no catalogue row. All
   seven added with key, partitions, retention and consumers, plus `create-topics.sh` entries and
   five new DLQs for the hops M7 will consume. Catalogue: **25 → 32 primary, 6 → 11 DLQ.**

4. **Scoring had two plausible homes.** §4.1.7 gives the rules engine to `threat-assessment`; §6.3.4
   describes `threat-scorer` as a rules engine plus residual, which reads as an AI service owning the
   whole thing. §4.1.7 amended to settle it: **the weighted rules engine is Track A code in
   `threat-assessment`**, and the gradient-boosted residual arrives as one factor on
   `ai.inference.completed.v1` — never a synchronous call. This keeps
   `track-correlation → ai-orchestrator` the pipeline's only synchronous link (CLAUDE.md §2), and
   makes the service fully testable with zero models, the same property §6.4 gives the rest of Track A.

**The advisory-only scope limit is now enforced by the build, not only by prose.** Two closed
vocabularies are asserted by test: `Recommendation` must hold exactly §6.3.4's five values, and
`EngagementAction` must hold exactly one legal value — `ENGAGEMENT_ACTION_NONE`. The latter is a
one-value enum by design: §2.4.3 renders "ENGAGEMENT ACTION — NONE, outside system scope", and
making it a typed field means no producer can set a value that claims otherwise and no consumer must
parse prose to be sure. A free-text field would have carried the same words with none of the
guarantee. Adding a value to either fails the build.

**Also deliberate:** severity reuses `threat.v1.ThreatLevel` rather than a parallel alert scale (one
fact, one source); `dedup_key` is published *and* unique-constrained in `alertdb`, because §5.7 names
duplicate alerts as the trust failure that outranks all others and application logic alone cannot
survive two instances processing one transition; evidence is referenced by URI + SHA-256, never
inlined, so a spectrogram never lands on a topic the gateway, reporting and audit all consume; and
neither notification event carries a channel address or message body, keeping personal data off
topics whose retention was chosen for telemetry (§7.9).

`verify-infra.sh`'s topic assertion was stale (`>= 26` against an actual 31) and is corrected to
`>= 43` with the twelve new topics named explicitly.

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
