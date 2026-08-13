# Project Handoff — AI-Enabled Secure C2 Platform for Counter-UAS Operations

> ## Status: **Locally verified / production deployment hardening pending**
>
> This system has been built, assembled and driven end to end **on a single development host**. Every
> claim in this document was verified by running it, and the evidence is cited.
>
> **It is not production-ready, and must not be described as such.** Five requirements remain
> unimplemented and unverified: **mTLS between services, secrets management, load and stress
> validation, Postgres RLS and Elasticsearch ILM, and deployment/DR**. Until each is implemented *and
> verified*, no production-readiness claim should be made on this system's behalf — in a report, a
> demonstration, or a conversation.

**Frozen at commit `e611a9f`** on `main`. No feature changes after this point.

| | |
|---|---|
| Repository | `cuas-platform` |
| Frozen commit | `e611a9fc0246f7787a19cc8e5e1a6c3a588fd9ed` |
| Milestones complete | M0 – M11, and M12.A |
| Source of truth | [`docs/ARCHITECTURE.md`](ARCHITECTURE.md) |

---

## 1. What this system is

A counter-UAS command-and-control platform: sensor detections are ingested, correlated into tracks,
classified by an AI boundary, scored for threat, and raised as alerts to an operator console that
updates live over a WebSocket.

**It is advisory only.** RF jamming, GNSS spoofing and cyber takeover are outside the software
boundary, and every recommendation terminates at an operator authorisation gate. This is not merely
documented — it is enforced by the build: `Recommendation` must hold exactly the five §6.3.4 values
and `EngagementAction` exactly `ENGAGEMENT_ACTION_NONE`, and the build fails if a value is added.
The console renders the engagement action as `NONE — outside system scope` structurally, because the
contract has no field that could say anything else.

### Shape

- **13 backend services** (Java 21 / Spring Boot 3.4), Clean/hexagonal per §4.3, database-per-service
- **1 React 19 operator console** (TypeScript strict, Tailwind 4, MapLibre), its own npm project
- **Kafka** event backbone, **PostgreSQL** (10 databases), **Redis**, **MinIO**, **Elasticsearch**
- **Protobuf contracts** via `buf`, shared by every service
- **7 Python AI service scaffolds** — see §5, these are *not* implemented

---

## 2. Verified state at `e611a9f`

Every row was produced by running the thing named, on this host, at this commit.

| Check | Command | Result |
|---|---|---|
| Backend build + tests | `mvn -f backend/pom.xml clean verify -Dbuf.skip=true` | **17 modules, 944 tests, 0 failures, 0 errors, BUILD SUCCESS** |
| Coverage gate | `… -Pcoverage-gate` | **All coverage checks met** (floor 0.40) |
| Frontend tests | `npm --prefix frontend test -- --run` | **69 tests / 25 files passed** |
| Frontend types | `npm --prefix frontend run typecheck` | clean |
| Frontend lint | `npm --prefix frontend run lint` | **0 errors**, 13 warnings |
| Frontend build | `npm --prefix frontend run build` | 178.78 KB gzip initial; MapLibre code-split |
| Container health | `docker compose ps` | **16 healthy** |
| Backup | `bash scripts/backup.sh` | 10 databases + MinIO, real content |
| Restore | `bash scripts/restore.sh <dir> configdb` | restored; service healthy after |
| Audit chain after restore | `POST /api/v1/audit/verify` | **`intact: true`, 109 records, no broken link** |
| Live operator journey | `bash scripts/verify-journey.sh` | **PASSED** |

### The end-to-end evidence

`scripts/verify-journey.sh` drives the **real built SPA** the `web` container serves, in Chromium,
with **no `page.route` and no `routeWebSocket`**. It signs in against the real auth-service through
the gateway and reads tracks and alerts off the gateway's own `/ws`.

The gateway is restarted immediately before the run, so its in-memory track view is empty and its
fan-out consumer sits at `latest`. The browser therefore observed:

```
CONNECTED {"sessionId":"06bae8cf-2c83-8e10-01f3-f48b97514079"}
SNAPSHOT  tracks  seq 1  {"tracks":[]}        <- the gateway starts empty
DELTA     tracks  seq 2…6  trackId 019ff99c-da95-7e98-8d07-a3e03e80d035
```

The empty first `SNAPSHOT` is what makes this evidence rather than a coincidence: every track the
browser received demonstrably arrived from *that run's* replay. An earlier pass additionally captured
the alert envelope in full — `ALT-4243`, `THREAT_LEVEL_MEDIUM`, whose `trackId` matched the track
delivered at `DELTA seq 3`, one object having traversed the entire pipeline:

```
sensor-simulator → sensor-ingestion → Kafka → track-correlation → ai-orchestrator (mock inference)
  → Kafka → threat-assessment → Kafka → alert-service → Kafka → api-gateway → WebSocket → browser
```

### Reproducing it

```bash
cp .env.example .env      # set CUAS_BOOTSTRAP_ADMIN_ENABLED=true and a password ≥12 chars
cd contracts && buf generate && cd ..
COMPOSE_PARALLEL_LIMIT=2 docker compose --profile infra --profile platform up -d --build
docker compose exec kafka bash /scripts/create-topics.sh
bash scripts/verify-platform.sh     # black-box, through the gateway only
bash scripts/verify-journey.sh      # real browser, real WebSocket
```

`COMPOSE_PARALLEL_LIMIT=2` is not advice. The Docker Desktop engine died twice under concurrent image
builds on a 7.4 GB engine, taking every container with it.

---

## 3. Deferred items — **preserved exactly as documented**

Reproduced verbatim from [`docs/ROADMAP.md`](ROADMAP.md) § "M12 remaining — not started, and honestly
so". **None of the following was attempted.**

- **mTLS between services with 24 h certs and rotation (§7.2, SR-2).** Needs cert-manager or SPIFFE
  in a real cluster. The gRPC surfaces already assume transport-level authentication, so this is
  provisioning, not redesign — but it is provisioning that cannot be done in `docker compose`.
- **Secrets manager (§7.7).** Local runs use `.env`, which is what the document prescribes for
  development. A production secrets backend is untouched.
- **Load and stress to the §1.x ceilings, and the 10k-track soak.** The engine here is 7.4 GB and
  died twice under concurrent image builds; a 10k-track soak is not a thing this machine can host,
  and a number produced on it would be misleading rather than useful.
- **The observability stack exercised under load.** It is version-controlled IaC from M8.D and comes
  up, but no alert rule has been tuned against real load and no trace has been followed end to end.
- **Postgres RLS (SR-3)** and **Elasticsearch ILM (SR-4)** remain as recorded at M8.D.
- **Failover and DR beyond backup/restore.** Single-node Postgres, single-broker Kafka. Backup and
  restore work; there is no replica to fail over to and no test of losing one.
- **Deployment to any environment, and the deploy/rollback half of §8.4's pipeline.** The workflows
  build, scan and sign images; nothing deploys them, and `docker-compose.ci.yml` does not exist.
- **§6.7 conformance bodies and Track B services (SR-6)**, per the decision recorded above.

### Two further limits verified at this commit

Neither is a defect; both will look like one to somebody who has not read this.

- **Tracks stay unclassified while track-correlation's breaker is open.** The mock inference provider
  samples a lognormal fitted so its p95 lands *exactly* on each service's configured timeout (§6.4) —
  deliberately, so the breaker is exercised — and sleeps for the sample. A full-burst replay overruns
  the `PT0.5S` Infer budget and the class column reads `——`. This is §6.5's specified degradation:
  tracks, threat scores and alerts are unaffected. Thinning the replay does *not* help — it drops
  below `required-consecutive-sweeps` and produces no tracks at all.
- **The login rate limit is real.** `LOGIN_PER_ACCOUNT` is 10/hour, burst 3 (§7.8), refilling about
  one token every six minutes. Repeated verification runs will meet a 429. **Do not probe the
  endpoint to check** — the probe is itself a login and consumes the token you are waiting for.

### Not verified at this commit

- **The `web` image was not rebuilt** after the final frontend commit: Docker Hub was unreachable
  (`EOF` on manifest HEAD) and the base images were not cached. The live journey therefore ran against
  an SPA built one commit earlier. The intervening change was a `useRef` → lazy `useState` refactor in
  `RealtimeContext` — behaviourally equivalent, but that equivalence was not demonstrated on a
  rebuilt image. **Rebuild `web` and re-run `scripts/verify-journey.sh` when the registry is
  reachable.**

---

## 4. Open security findings

Full detail in [`docs/SECURITY-REVIEW.md`](SECURITY-REVIEW.md).

| # | Severity | Finding |
|---|---|---|
| ~~SR-1~~ | — | **Closed.** `gitleaks` gates the backend pipeline over full history. |
| **SR-2** | Medium | mTLS material and 24 h rotation not provisioned. **Blocks production.** |
| **SR-3** | Low | Owner-scoping enforced in application code, not Postgres RLS. |
| **SR-4** | Low | Elasticsearch ILM retention not active. |
| ~~SR-5~~ | — | **Closed.** Pre-commit `gitleaks` hook added. |
| **SR-6** | Low (by design) | No Track B service exists to test — see §5. |
| ~~SR-7~~ | — | **Closed.** JaCoCo bound; measured coverage gate enforced in CI. |
| **SR-8** | Low | An unsupported HTTP verb renders **500**, not 405. No information leaks; a client cannot distinguish "wrong verb" from "server broken". |

The authentication, authorisation, audit and rate-limiting controls of §7 **are** implemented and
enforced server-side, with fail-closed auditing. That is the core of the system's security posture and
it is real. What is missing is transport security between services and a secrets backend.

---

## 5. The AI boundary — read this before demonstrating it

The AI boundary runs end to end **with zero trained models**, and this is by design (§6.1, §6.4).

- `ai-orchestrator` routes inference by **sensor modality**, never by service identity. A test
  (`ModelAgnosticismTest`) fails the build if a §6.3 service or framework name appears anywhere in its
  source, comments included. Adding a model is a configuration entry, not a code change.
- The provider in use is the **Java `MockInferenceProvider`** inside `ai-orchestrator`. It is covered
  by that module's tests and was exercised live throughout M11.
- **The seven Python services under `ai-services/` are empty scaffolds** — `src/**` contains only
  `__init__.py` packages; the mock provider directory holds `.gitkeep` files. There is no gRPC server
  and no inference code. Their §6.7 conformance suites are `@pytest.mark.skip` over unimplemented
  bodies.
- Consequently `ai-service-ci.yml` proves that dependencies resolve, bindings generate and suites
  collect — **not** that any service honours the §6.2 contract. It is wired now so the suites gate the
  build the day an implementation lands.
- The AI **image-publish job was deliberately removed**: those Dockerfiles build successfully, but the
  resulting image's `CMD` names a module no file defines. A signed, scanned, green-ticked image of a
  service that cannot start is worse than no image.
- Three trained models (RF, Vision, Acoustic) exist as **external Kaggle notebooks**. They are not
  production services and are not integrated. Promoting Track B requires an architecture amendment.

---

## 6. Where things are

| Path | What |
|---|---|
| `docs/ARCHITECTURE.md` | Single source of truth. Everything else defers to it. |
| `docs/ROADMAP.md` | Milestone history, M12 remaining, per-milestone decisions |
| `docs/OPERATIONS.md` | Runbook: start-up, verification, backup/restore, failure table |
| `docs/DEVELOPMENT.md` | Building, toolchain, running the stack |
| `docs/SECURITY-REVIEW.md` | §7 control-by-control review and findings |
| `CLAUDE.md` | Engineering directives and current project state |
| `backend/` | 17 Maven modules |
| `frontend/` | React console — **separate npm project, not in the Maven reactor** |
| `contracts/` | Protobuf; `contracts/generated` is git-ignored build output |
| `scripts/` | `verify-infra.sh`, `verify-platform.sh`, `verify-journey.sh`, `backup.sh`, `restore.sh` |
| `.github/workflows/` | Four working pipelines |
| `infrastructure/monitoring/` | Prometheus/Grafana/Loki/Tempo as IaC |

Two traps worth knowing before touching the build:

- **`java`, `mvn` and `buf` are not on `PATH`** — they live in `../.toolchain/`. Never pipe `mvn` into
  `tail`; it masks failure as exit 0.
- **`contracts/generated` is git-ignored.** Run `buf generate` in `contracts/` before building images;
  the Dockerfiles pass `-Dbuf.skip=true` and fail fast with a readable message if the bindings are
  absent.

---

## 7. Recommended next steps, in order

Ordered by what blocks a production claim, not by effort.

1. **mTLS between services** (SR-2) — cert-manager or SPIFFE in a Kubernetes overlay. Every
   inter-service gRPC hop is currently plaintext. This is the largest single gap.
2. **Secrets management** (§7.7) — a real backend, replacing `.env`.
3. **Load and stress validation** — to the §1.x track ceilings, plus the 10k-track soak, on hardware
   that can host it. This is where the next class of defect lives: the one production-readiness defect
   found during M12 (Postgres connection saturation) appeared under load, not at startup.
4. **Deployment and DR** — the deploy/rollback half of §8.4, a Postgres replica, a multi-broker Kafka,
   and a tested failover.
5. **Postgres RLS (SR-3) and Elasticsearch ILM (SR-4).**
6. **SR-8** — map `HttpRequestMethodNotSupportedException` to 405.
7. **Track B**, if and when trained models are to be integrated — an architecture amendment first.

---

## 8. A note on how to describe this system

The work is genuinely substantial: seventeen modules, 944 backend tests, a complete operator console,
a verified end-to-end runtime, a real CI pipeline, and a tested backup and restore. It is reasonable
to be pleased with it.

It is also, right now, a system with **no transport security between its services, no secrets
backend, and no evidence whatsoever about its behaviour under load**. Those three facts are not
softened by the quality of everything else.

The honest description is the one at the top of this document: **locally verified, production
deployment hardening pending**. Use it as written.
