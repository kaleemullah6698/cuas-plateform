# CUAS Platform — Engineering Directives

You are the lead software architect for the AI-Enabled Secure C2 Platform for Counter-UAS Operations.

`docs/ARCHITECTURE.md` is the **single source of truth**. These directives are always in force.

---

## 0. Scope guard — read this first

**Never add a feature that is not described in `docs/ARCHITECTURE.md`.**

If asked for something outside it:
1. Say so explicitly, and name the section it conflicts with or is absent from.
2. Explain the consequence of adding it.
3. **Ask for confirmation before implementing.** Do not proceed on assumption.

Never, without explicit approval:
- Rename a service, topic, or database
- Change an existing API contract or database schema
- Redesign any part of the architecture
- Break compatibility with the AI service contract (§6.2)

If `ARCHITECTURE.md` is silent, ambiguous, or wrong on something needed — **raise it and get the document amended first.** Code that contradicts the architecture creates a second, undocumented source of truth, which is worse than a gap.

### Hard scope limit — engagement

This platform produces **advisory recommendations only**. RF jamming, GNSS spoofing, and cyber takeover are outside the software boundary. Every recommendation terminates at an operator authorisation gate.

Never add a code path that emits an RF-active or link-altering command. The recommendation vocabulary is closed (§6.3.4). Extending it requires an architecture amendment and supervisor approval — not a pull request.

---

## 1. Before writing code

For any non-trivial change, state these before implementing:

1. **Architecture analysis** — which services, layers, and contracts are affected
2. **Dependencies** — what this needs, what breaks if it changes
3. **Reusable components** — check `backend/platform-commons/` and `frontend/src/design-system/` **before** writing anything new
4. **Implementation plan** — the steps, in order

Never generate a quick prototype. Never introduce technical debt. Every feature must be modular, reusable, testable, and scalable.

---

## 2. Architecture rules

### Dependency direction — inward, always

Backend services use the Clean/hexagonal layout in §4.3:

```
api/  ──────────┐
                ├──▶  domain/   ◀── the centre. Depends on nothing.
infrastructure/ ┘
```

| Package | May import | May **not** import |
|---|---|---|
| `domain/` | JDK only | Spring, JPA, Kafka, gRPC, `api/`, `infrastructure/` |
| `api/` | `domain/`, Spring Web | `infrastructure/` |
| `infrastructure/` | `domain/`, frameworks | `api/` |

**`domain/` must compile without Spring on the classpath.** That is the test. `@Entity`, `@Component`, or `@Autowired` in a domain class is a layering violation.

- Business logic never goes in a controller. Controllers translate HTTP and delegate.
- Repository *interfaces* are declared in `domain/`; implementations live in `infrastructure/persistence/repository/`.
- Domain models and JPA entities are **separate types**, mapped explicitly. This duplication is deliberate.
- Domain models never cross the API boundary — `api/dto/` types are the wire contract.

> Note: a plain `Controller → Service → Repository → Database` chain is *layered*, not Clean. The difference matters: here the domain sits at the centre and persistence is an outward adapter. §4.3 governs.

### Service boundaries

- **Database-per-service is absolute.** No service reads another's tables. Cross-service data flows via API or event only.
- No synchronous call between pipeline services except `track-correlation → ai-orchestrator`.
- Nothing calls `audit-service` — it consumes events. It can never become an availability dependency.

---

## 3. Coding standards

### Java

- **Java 21.** Records for DTOs and value objects. Sealed interfaces for closed hierarchies. Pattern matching over `instanceof` chains.
- **Constructor injection only.** Never field injection. A single constructor needs no `@Autowired`.
- **Immutability by default** — `final` fields, unmodifiable collections, no setters on domain models.
- **`Optional` for absent return values.** Never for fields or parameters. No `null` returns from public methods.
- **Package-private by default.** Widen only when a caller outside the package needs it.
- **Prefer composition over inheritance.** No `Base*Service` classes.
- **Avoid static methods** except genuinely pure functions with no dependencies.
- **Lombok only where it earns its place** — records and constructor injection remove most of the need.
- **No `util` packages.** Helpers belong with the concept they serve.
- **No duplicated code.** Shared concerns go to `platform-commons`.

### TypeScript / React

- **`strict: true`. No `any`** — use `unknown` and narrow.
- Function components with typed props. No `React.FC`. Named exports except route components.
- **Four state domains, four tools** (§3.4): TanStack Query (server), external stores (streaming), Zustand (UI), React Hook Form (forms). Never conflate them.
- **Streaming data never passes through React state per-message** — external store, coalesced to `requestAnimationFrame` (§3.4). This is the single most important frontend performance rule.
- `design-system/` never imports from `features/`. Features never import each other.

---

## 4. Every REST endpoint

Non-negotiable. An endpoint missing any of these is incomplete:

- [ ] **Validation** — Jakarta Bean Validation on the request DTO, `@Valid` at the parameter
- [ ] **Exception handling** — via `@RestControllerAdvice`; never a raw stack trace to a client
- [ ] **Logging** — entry at DEBUG, failure at WARN/ERROR, always with the correlation ID
- [ ] **DTOs** — distinct request and response records; never domain models, never `Map<String,Object>`
- [ ] **OpenAPI** — `@Operation` and `@ApiResponse` for every status returned
- [ ] **Authorisation** — `@PreAuthorize` with the permission from the §7.4 RBAC matrix

Errors use RFC 7807 `application/problem+json` with a `correlationId`.

---

## 5. Security — always in force

- **No secret in source control, in a container image, or in a production environment variable.** Use environment variables locally, the secrets manager in production.
- **Permissions are enforced server-side.** UI gating is convenience, never a control.
- **Every mutation is audited.** If the audit log is unavailable, security-relevant operations **fail closed** (§7.5).
- **AI services have no database credentials and no network egress.** They are pure functions (§7.1).
- Validate all input at the boundary. Never interpolate user input into a query.

---

## 6. Git

- **Every commit leaves the codebase working** — it compiles, tests pass, the app starts.
- One feature per commit. Small commits.
- Conventional Commits, scoped to the module: `feat(track-correlation): add Kalman state estimation`
- Branch from `develop`. **Never break `main`.**

---

## 7. Task playbooks

Detailed procedures live in `.claude/skills/`. Invoke by name when the task matches:

| Skill | Use when |
|---|---|
| `add-backend-service` | Creating or fleshing out a Spring Boot service |
| `add-rest-endpoint` | Adding an HTTP endpoint |
| `add-frontend-feature` | Building a screen, component, or feature slice |
| `integrate-ai-service` | Wiring an AI service, mock provider, or inference contract |
| `write-tests` | Adding tests at any layer |
| `apply-security-controls` | Implementing auth, RBAC, audit, or rate limiting |
| `document-api` | Writing OpenAPI, README, or architecture documentation |

---

## 8. Project state

**M0–M9 complete; M10 in progress (A shell + B auth/RBAC + C dashboard + D map + E tracks + F alerts/incidents done).**
Backend: full `mvn clean verify` green — **17 modules, 933 tests, 0 failures, BUILD SUCCESS**. Frontend
(`frontend/`, its own npm build): **64 Vitest tests + 1 Playwright E2E**, `tsc`/`vite build` clean
(map route code-split so MapLibre loads on demand — initial bundle 174 KB gzip). Infra verified live
(`scripts/verify-infra.sh`: 33). See `docs/ROADMAP.md` per milestone and `docs/SECURITY-REVIEW.md`
for §7.

**Roadmap amended (owner's direction): M10 professional operator console → M11 runtime deployment &
full integration → M12 production readiness** (defined in `docs/ROADMAP.md` Phase 5). M10 builds the
deferred full §2/§3 console on the real backend contracts, in committed groups: **A** ✅ design system
(shadcn-style: Radix primitives + Tailwind 4 token theme — semantic + threat/status ramps) + the
command-center `AppShell`; **B** ✅ auth — a canonical `src/api/client.ts` (auth header + refresh-on-401
+ correlation id), JWT-decoded identity/permissions in the store, the rebuilt login (RHF+Zod, MFA
step), `SessionManager` (bootstrap-restore from the refresh cookie + proactive silent refresh),
profile page, and role-based UI (`Can`/`RequirePermission`, permission-gated nav + route guards);
**C** ✅ dashboard; **D** ✅ MapLibre map — imperative (outside React, `setData` at the §2.5.1 10 Hz
budget, code-split), live tracks styled by alert-derived severity (colour + shape + label), heading
vectors, client-accumulated capped/evicted trails, crit/high never clustered (§2.5.4), shared
selection via `src/store/`, command-centre glass overlays; a **layered registry** where zones/sensors/
prediction/offline-tiles are built but disabled-until-M11 (Option A — no fabricated data; M11 flips a
flag, not a rewrite); **E** ✅ track console — Screen 4/§2.4.2: a threat-sorted triage queue (J/K/Enter/
Esc keyboard, threat-floor + text filter) master-detail with a live track-detail panel (identity, class
+ confidence, telemetry, client-accumulated velocity/altitude session sparklines); server-authoritative
detail (history, sensor contributions, AI/score, prediction) built + typed against the real `/tracks`
DTOs but gated behind `TRACKS_REST_ROUTED` (Option A — G-4 covers `/tracks` too; awaiting state, no
fabrication); introduced **server state** (TanStack Query + one `QueryClient`) and lifted
`severityByTrack` to `src/lib/`; **F** ✅ Alert Center + Incident Investigation (Screens 7–8/§2.4.3):
a three-bucket alert queue filled **live today** from the WS raised feed (open-source-track works now),
with ack/assign/resolve + the whole incident screen (tabbed timeline/notes/evidence/decision-chain,
declare/close/note mutations) built + typed against alert-service DTOs but gated behind
`ALERTS_REST_ROUTED`/`INCIDENTS_REST_ROUTED` (G-4 — awaiting state, no fabrication); the decision
chain renders engagement `NONE — outside system scope` structurally; **G** reports/config/users/audit/notifications.
**Two items are M11, not M10:** the gateway routes for
the bare-path services (alert/config/report/notification/**tracks** — M9 finding G-4) and self-hosted map tiles.

**M9 — real-time edge & thin frontend verification** (approved plan, grouped A→D): **M9.A** ✅ gateway
WebSocket fan-out (14 tests). The gateway now consumes `track.*`/`alert.raised`/`sensor.health` and
fans them to the browser over `/ws` — JSON envelope (`CONNECTED`/`SNAPSHOT`/`DELTA`/`ALERT`/
`HEARTBEAT`/`ERROR`) per §3.5, per-channel monotonic `seq`, 5 s heartbeat, coalesced track/sensor
deltas (alerts immediate), JWT-on-upgrade validated by the existing JWKS decoder incl. revocation,
per-channel authorisation (`alerts` needs `alert:read`), and the §7 five-connections-per-user limit.
**Two approved decisions (D-1, D-2):** the `SNAPSHOT` is built from the gateway's own in-memory track
view — `track.state.snapshot.v1` has **no producer** and its production is deferred — and the session
registry is in-memory (Redis-backed registry deferred). Both recorded in `docs/ARCHITECTURE.md` §3.5
and the §5.2 footnote. **M9.B** ✅ frontend realtime core (12 Vitest tests): the performance-critical
non-visual layer — `WebSocketClient` (token-on-upgrade, backoff+jitter reconnect, heartbeat-timeout,
seq-gap→resnapshot, two-gaps→degraded), the `TrackStore` external store (Map + rAF-coalesced flush,
ring-capped, `useSyncExternalStore` — track updates never pass through React state per message), the
`AlertStore` (immediate, never coalesced), `ConnectionStore`, and the auth client/store (access token
in memory only, §7.3). TS types mirror the gateway envelope exactly. **M9.C** ✅ thin UI (17 frontend
tests total; `vite build` clean at 79 KB gzip, under the §3.6 budget): a login screen (with the MFA
branch), a `ProtectedShell` that guards routes and — because the token is memory-only — sends a
refreshed tab back to login, the live `TrackConsole` bound to the external store, the immediate
`AlertStrip`, and the §2.5.2 connection-lost/degraded banner with track desaturation. React Router,
one `Realtime` instance via context. **Frontend is a separate npm project** (`frontend/`), verified
with `npm --prefix frontend test` / `typecheck` / `build` — NOT in the Maven reactor, so `mvn clean
verify` does not cover it. **M9.D** ✅ E2E + wiring + verify: the Playwright `intrusion→alert` spec
drives the *real built SPA* (sign in → track via `SNAPSHOT`+`DELTA` → critical alert on the strip)
with only the gateway REST/WS mocked in the browser — it **passes** (Chromium installed per R-1). The
`web` compose service builds the SPA and serves it behind nginx with an SPA fallback and a reverse
proxy of `/api` and `/ws` to the gateway (same-origin browser). Full `mvn clean verify` green (933
tests). **M9 is complete**, and with it the M0–M9 backend-first build.

> **Building:** `java`, `mvn` and `buf` are not on `PATH` — they live in `../.toolchain/`. See the
> toolchain note in `docs/DEVELOPMENT.md`, and never pipe `mvn` into `tail` (it masks failure as
> exit 0). The contract sources are already generated under `contracts/generated/java`; build with
> `-Dbuf.skip=true` when `buf` is not on `PATH`.

Built: `contracts` (§5.3, §5.3.1–§5.3.5, §6.2, audit, user, gateway, ingestion, track +
`TrackService`, threat, alert, notification, **config + report (M8.A)**), `platform-commons`,
`audit-service`, `user-service`, `auth-service`, `api-gateway`, `sensor-ingestion`,
`sensor-simulator`, `track-correlation`, `ai-orchestrator`, `threat-assessment`, `alert-service`,
`notification-service`, **`config-service` (M8.B)**. The remaining service (`reporting-service`) is
still scaffold; the observability/IaC stack and the §7 hardening pass remain (M8.C/D).

**M8 in progress — config, reporting, observability, hardening.** M8.A ✅ (config.proto +
report.proto + §5.2 catalogue amendments — 37 primary topics). M8.B ✅ config-service (27 tests):
namespaced runtime configuration with a full version history and before/after change-log audit
(written in the same transaction as the change — fail-closed), rollback, and change propagation over
the gRPC `WatchConfig` server stream (§4.1.12's authoritative, DB-backed push) plus the compact
`platform.config.*` topics. Writes are gated per namespace kind by §7.4 permissions
(`policy:write`/`sensor:configure`/`ai:configure`/`config:write`); reads on `policy:read` — the same
"use only matrix permissions, flag the gap" decision notification-service made. M8.C ✅
reporting-service (23 tests): templated async reports (incident, daily summary, sensor performance,
fusion efficacy) exported PDF/JSON/CSV. Built lean per the user's call — it consumes the domain
topics into an **event-projection read model in its own reportdb** and aggregates over that (no
cross-service DB reads); the Elasticsearch mirror is deferred behind the `ReportEventStore` port.
Async job engine (202 + poll + download) on a bounded pool; artifacts filesystem-stored with sha256;
`report.generation.started/generated/failed` published. RBAC `report:read`/`report:generate`.
M8.D ✅ observability + hardening: the Prometheus/Grafana/Loki/Tempo stack as version-controlled IaC
under `infrastructure/monitoring/` (scrape config, alert rules incl. the audit-chain-break page, the
four §8.6 dashboards, datasource + dashboard provisioning), wired into docker-compose under an
`observability` profile; and `docs/SECURITY-REVIEW.md`, a §7 pass confirming auth/RBAC/audit/rate-
limiting are implemented and enforced server-side, with four tracked findings (gitleaks CI wiring,
mTLS provisioning, Postgres RLS, ES ILM). **M8 is complete.**

**M6 complete** (`ai-orchestrator` + the mock provider). The AI boundary runs end to end with zero
trained models: modality routing, per-service timeout/breaker/bulkhead, content-addressed cache,
canary, the four `ai.*` topics, the admin API, and track-correlation's circuit-broken `Infer` call
producing `track.classified.v1`. Frontend is deferred to M9; `npm ci` has never been run.

**M7 complete — assessment & alerting** (`threat-assessment`, `alert-service`,
`notification-service`), delivered in four groups: **A** contracts & amendments ✅, **B**
threat-assessment ✅ (120 module tests), **C** alert-service ✅ (63 module tests), **D**
notification-service ✅ (54 module tests). A scored threat transition raises a deduplicated,
escalating alert; an operator declares an incident; and each event fans out to the recipients who
should hear it, on their preferred channel, with DB-backed retry (no Redis — `FOR UPDATE SKIP
LOCKED`) and delivery receipts. Delivery is mock-first (§6.4): WebSocket/email/SMS are simulated in
the dev stack, webhook is real HTTP. The `notification.sent/.failed` events carry attempt outcomes,
never message content or a channel address (personal data stays in `notifydb`). The self-service
endpoints gate on authentication and scope to the JWT subject — no §7.4 permission covers a user's
own preferences, and §0 forbids inventing one.

**M7.A complete.** `threat.proto`, `alert.proto`, `notification.proto` authored (§5.3.3–§5.3.5);
seven topics catalogued (25 → 32 primary, 6 → 11 DLQ); §6.3.4 amended with normative weights and
the LOW/MEDIUM/HIGH thresholds §2.4.2 never stated; §4.1.7 amended to place the rules engine in
`threat-assessment` (the AI residual is one factor on an event, never a synchronous call); §4.1.8
and §4.1.9 amended to consume the `sensor.health.v1` that actually exists.

**The advisory-only limit is enforced by test.** `Recommendation` must hold exactly §6.3.4's five
values and `EngagementAction` exactly one — `ENGAGEMENT_ACTION_NONE`. Both fail the build if a value
is added, which is what makes §0's scope limit a property of the build and not only of the document.

**The orchestrator must never name a model.** §6.1's guarantee — a new model is a configuration
entry, not a code change — is enforced by `ModelAgnosticismTest`, which fails the build if a §6.3
service or framework name appears anywhere in `ai-orchestrator` source, comments included. Route by
*modality* (a fact about the sensor), never by service identity.

**On the AI models:** three trained models (RF, Vision, Acoustic) exist as Kaggle notebooks. They
are *not* production services and are not to be integrated directly. **No model conversion or ONNX
packaging happens during M6** — M6 builds the boundary; the notebooks stay external assets. M6 builds the orchestrator with
the mock provider as the default; the real services arrive later behind the same `InferenceProvider`
port (§6.1, §6.4) with **no orchestration change**. `acoustic-classifier` is §6.3.5 "future" —
promoting it needs an architecture amendment, agreed as a follow-on sub-milestone.
