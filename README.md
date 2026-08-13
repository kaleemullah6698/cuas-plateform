# AI-Enabled Secure C2 Platform for Counter-UAS Operations

Software integration layer between counter-UAS detection hardware and engagement assets that remain under human authority. Ingests heterogeneous sensor streams (RF, radar, EO/IR, acoustic, GNSS), normalises them into a canonical detection record, correlates them into tracks, invokes specialised AI services per modality, fuses their evidence, scores each track for threat, and presents an operator with a decision.

**Institution:** National University of Sciences and Technology (NUST) — Department of Computer & Software Engineering
**Team:** Kaleem Ullah · Muhammad Ahmad Ameen · Syed Rehan Ejaz Shah
**Supervisor:** Dr. Muhammad Shafiq

> **Scope constraint.** Engagement actions — RF jamming, GNSS spoofing, cyber takeover — are outside the software boundary. The platform emits advisory recommendations only, from a closed vocabulary, always terminating at an operator authorisation gate. There is no code path in this system that emits an RF-active command.

---

## Status: locally verified / production deployment hardening pending

The platform is **built, assembled and driven end to end on a development host**. Milestones M0–M11
and M12.A are complete. Frozen at commit `e611a9f`.

Verified at that commit, by running it: **17 modules, 944 backend tests, 0 failures**; frontend
**69 tests**, `tsc` and lint clean; **16 containers healthy**; backup and restore of all ten
databases with the audit hash chain verified **intact** afterwards; and the **live operator journey**
— a real browser, a real login, and a real WebSocket carrying a real track and alert, with no mocks.

> **It is not production-ready, and must not be described as such.** Five requirements remain
> unimplemented and unverified: **mTLS between services, secrets management, load and stress
> validation, Postgres RLS / Elasticsearch ILM, and deployment/DR**. Every inter-service gRPC hop is
> currently plaintext, credentials come from `.env`, and there is no evidence at all about behaviour
> under load. No production-readiness claim should be made until each is implemented *and* verified.

**Start here:** [`docs/HANDOFF.md`](docs/HANDOFF.md) — the verified state, the deferred list, and the
recommended next steps. [`docs/OPERATIONS.md`](docs/OPERATIONS.md) is the runbook.

`docs/ARCHITECTURE.md` is the single source of truth. Every directory here traces to a section of it.

---

## Repository layout

```
cuas-platform/
├── backend/            13 Spring Boot services + contracts + parent POM + shared commons (17 modules)
├── frontend/           React 19 + TypeScript + Vite operator console
├── ai-services/        6 Python inference service scaffolds + mock provider — NOT implemented;
│                       the working provider is the Java MockInferenceProvider in ai-orchestrator
├── contracts/          Protobuf definitions — the cross-track contract
├── infrastructure/     Docker, K8s, Postgres, Kafka, Redis, ES, monitoring
├── docker/             Shared Docker assets
├── scripts/            Developer and operational tooling
├── tools/              Sensor simulator
├── docs/               Architecture and engineering documentation
└── .github/workflows/  CI/CD pipelines
```

### Backend services — [ARCHITECTURE.md §4.1](docs/ARCHITECTURE.md)

| Service | Port | Database | Responsibility |
|---|---|---|---|
| `api-gateway` | 8080 | — | Sole ingress: TLS, JWT validation, rate limiting, WebSocket fan-out |
| `auth-service` | 8081 | `authdb` | Credentials, TOTP MFA, JWT issue/rotate/revoke |
| `user-service` | 8082 | `userdb` | Users, roles, effective-permission resolution |
| `sensor-ingestion` | 8083 | `sensordb` | Normalisation to the canonical `DetectionRecord` |
| `track-correlation` | 8084 | `trackdb` | Association, Kalman state, track lifecycle, CPA |
| `ai-orchestrator` | 8085 | — | Track A/Track B boundary: routing, circuit breaking, mock fallback |
| `threat-assessment` | 8086 | `threatdb` | Composite scoring, rules engine, advisory recommendations |
| `alert-service` | 8087 | `alertdb` | Alert lifecycle, escalation, incidents, evidence bundles |
| `notification-service` | 8088 | `notifydb` | Multi-channel delivery, preferences, receipts |
| `reporting-service` | 8089 | `reportdb` | Templated async report generation |
| `audit-service` | 8090 | `auditdb` | Append-only hash-chained tamper-evident log |
| `config-service` | 8091 | `configdb` | Runtime configuration without redeploy |

Database-per-service is absolute: no service reads another's tables. All cross-service data flows via API or event.

### AI services — [ARCHITECTURE.md §6.3](docs/ARCHITECTURE.md)

| Service | Port | Dataset | Scope |
|---|---|---|---|
| `rf-classifier` | 9001 | DroneRF | Active |
| `vision-detector` | 9002 | VisioDECT | Active |
| `radar-rf-fusion` | 9003 | Multi-Sensor Drone Dataset 2026 | Active |
| `threat-scorer` | 9004 | Rules + ensemble | Active |
| `acoustic-classifier` | 9005 | RGB+Thermal+Audio | Future |
| `gnss-anomaly` | 9006 | Synthetic + public GNSS | Future |

Each AI service is a pure function of its input: no database credentials, no Kafka access, no network egress.

---

## Two-track development

The platform (Track A) and the AI services (Track B) are built in parallel and meet at exactly one place: the `InferenceService` gRPC contract in `contracts/proto/inference.proto`.

**Mock inference providers** let the entire platform run end-to-end with zero trained models. Mocks report `model_version: "0.0.0-mock"` and `device: DEVICE_MOCK`, so mocked results are never mistakable for real ones — the dashboard renders an explicit badge. Switching a service from mock to real is a configuration change:

```bash
AI_PROVIDER_MODE=mock docker compose --profile platform up -d
```

---

## Getting started

Prerequisites: JDK 21, Maven 3.9+, Node 22+, Python 3.12+, Docker with Compose.

```bash
cp .env.example .env        # then fill in credentials
```

Neither the Maven build nor the frontend install has been executed against this scaffold yet — see [docs/DEVELOPMENT.md](docs/DEVELOPMENT.md) for the intended commands and their current verification status.

---

## Highest-priority next step

`contracts/proto/detection.proto` and `contracts/proto/inference.proto` are empty. Both are specified in full in ARCHITECTURE.md §5.3 and §6.2 and can be lifted verbatim.

Until they exist, Track B has nothing to build against and the mock-provider decoupling does not function. These are the highest-value files in the repository.

---

## Documentation

| Document | Contents |
|---|---|
| [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) | **Single source of truth.** System design, all sections. |
| [docs/DEVELOPMENT.md](docs/DEVELOPMENT.md) | Environment setup and workflow |
| [docs/CODING-STANDARDS.md](docs/CODING-STANDARDS.md) | Clean Architecture, DDD, SOLID conventions |
| [docs/API.md](docs/API.md) | API contract reference |
| [CONTRIBUTING.md](CONTRIBUTING.md) | Branch strategy, commits, review |
