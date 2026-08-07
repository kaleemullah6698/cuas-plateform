# Development

## Prerequisites

| Tool | Version | Purpose |
|---|---|---|
| JDK | 21 | Backend services |
| Maven | 3.9+ | Backend build |
| Node.js | 22+ | Frontend build |
| Python | 3.12+ | AI services |
| Docker | Desktop, with Compose v2 | Full-system development, integration tests |
| `buf` | latest | Protobuf lint, breaking-change detection, codegen |

### Installing Docker Desktop (Windows) — a manual, admin step

> **Status:** installed and verified in the development environment — Docker Engine 29.6.1,
> Compose v5.3.0, WSL 2 backend. The infrastructure profile has been brought up and fully
> verified (see the table below). These steps are retained for a fresh machine.

Docker Desktop cannot be installed by an unprivileged or non-interactive process; it needs
administrator elevation, license acceptance, and usually a reboot. Install it once, by hand:

1. Download **Docker Desktop for Windows** from `https://www.docker.com/products/docker-desktop/`.
2. Run the installer **as Administrator**; keep the **WSL 2** backend option enabled.
3. Reboot if prompted, then launch Docker Desktop and accept the service agreement.
4. Ensure your account is in the **docker-users** group (the installer normally adds it).
5. Confirm: `docker version` and `docker compose version` (Compose **v2**).

Once Docker is running, `bash scripts/verify-infra.sh` performs the entire infrastructure
acceptance test end-to-end (see [Full system with Docker](#full-system-with-docker)).

### Restarting a stopped engine, without the GUI

Docker Desktop can stop between builds (resource pressure, a host sleep, or simply being closed).
Every Testcontainers test then fails with `Could not find a valid Docker environment` — a red reactor
that is **not** a code defect. Installation needs the GUI, but a restart does not:

```powershell
& "$env:LOCALAPPDATA\Programs\DockerDesktop\resources\bin\docker.exe" desktop start
```

Then poll `docker info` until it answers before re-running the build; the engine is usually ready in
under ten seconds but the CLI returns before it is. Note the executable is under
`%LOCALAPPDATA%\Programs\DockerDesktop` on this machine, not the `C:\Program Files\Docker` path most
documentation assumes.

---

## Verification status

**Important — read before following any command below.** This table records what has actually been executed and observed to succeed, versus what remains intended. It is updated as each milestone verifies its commands.

| Command | Verified | Notes |
|---|---|---|
| `python scaffold` (structure generation) | ✅ Executed | 429 directories, 595 files |
| Toolchain (`java -version`, `mvn -v`) | ✅ Executed | Portable Temurin JDK 21.0.11 + Maven 3.9.9 under `../.toolchain/` (git-ignored) |
| `mvn -pl platform-commons -am verify` | ✅ Executed | M0 — BUILD SUCCESS, 23/23 tests pass |
| `bash -n init-databases.sh` | ✅ Executed | Syntax OK (superseded by the live run below) |
| `buf lint` / `buf build` | ✅ Executed | M1 — clean (documented exceptions for the fixed contract) |
| `buf generate` (Java + Python + TS) | ✅ Executed | M1 — 50 Java, 6 Python, 2 TS files generated |
| `mvn -pl contracts -am verify` | ✅ Executed | M1 — BUILD SUCCESS, 15/15 serde tests; runs `buf generate` then compiles generated Java |
| Python bindings import + round-trip | ✅ Executed | M1 — protobuf 5.29 + grpcio on Python 3.14; round-trip + gRPC stubs verified |
| TypeScript bindings `tsc --noEmit` (strict) | ✅ Executed | M1 — protobuf-es v2; typecheck passed |
| `bash -n create-topics.sh` + dry-run | ✅ Executed | M1 — syntax OK; dry-run enumerates 24 topics (18 primary + 6 DLQ) |
| Full backend `mvn clean install` (16 modules) | ✅ Executed | BUILD SUCCESS, 38/38 tests (23 commons + 15 contracts); `buf generate` runs inside the build |
| `docker compose --profile infra up -d` | ✅ Executed | All 7 infra containers up; postgres/kafka/schema-registry/redis report healthy |
| `init-databases.sh` live against PostgreSQL 17 | ✅ Executed | Ran automatically on first init; **all 10 databases created**, per-database connections verified |
| `create-topics.sh` live against Kafka | ✅ Executed | **26 topics created** (20 primary + 6 DLQ); partitions/retention/cleanup match §5.2; idempotent on re-run |
| `scripts/verify-infra.sh` (DBs + topics + health) | ✅ Executed | **33 passed, 0 failed** — re-run at M3 against the authenticated Redis and the 26-topic catalogue |
| Kafka produce/consume round-trip | ✅ Executed | Message produced and consumed; host listener `localhost:29092` correctly advertised |
| Host reachability of all mapped ports | ✅ Executed | 5432, 29092, 8081, 9200, 6379, 9000, 9090, 5601 all reachable |
| `mvn clean verify` (M3 complete) | ✅ Executed | 16 modules, **256 tests**, 0 failures |
| `buf lint` / `buf breaking` / `buf generate` (M4.0) | ✅ Executed | `ingestion.proto` added — lint clean, breaking clean (additive), Java/Python/TS regenerated |
| `mvn clean verify` (M4.0 contracts) | ✅ Executed | 16 modules, **256 tests**, 0 failures — new `IngestAck`/`SensorHealth`/`DetectionIngestServiceGrpc` compile into the contracts jar |
| `create-topics.sh` catalogue (M4.0) | 📋 Declared | Added `platform.config.sensor.updated.v1`; script now declares **28 topics (22 primary + 6 DLQ)**. Live creation deferred to the next infra bring-up (M4 runtime); topic auto-creation stays disabled by design |
| `mvn clean verify` (M4.1 sensor-ingestion) | ✅ Executed | 16 modules, **297 tests** (+41), 0 failures — sensor-ingestion: 38 unit + 3 Testcontainers integration |
| sensor-ingestion integration tests | ✅ Executed | 3 against real PostgreSQL 17 + Kafka (cp-kafka 7.8.0) + Redis 7.4 (Testcontainers) — a posted detection emerges normalised on `sensor.detection.normalized.v1` with UUIDv7 + `ingest_time` stamped and the `sensordb` rows written; a repeated `(sensorId, sensorSeq)` is normalised once and counted duplicate; an invalid detection is rejected at intake |
| `mvn clean verify` (M4.2 replay + simulator) | ✅ Executed | **17 modules, 321 tests** (+24), 0 failures — new `sensor-simulator` module (13 tests) + replay/health/load coverage |
| replay end-to-end + load (Testcontainers) | ✅ Executed | A replayed NDJSON dataset is normalised through the real pipeline; a **2,000-detection load** run has every detection normalised (≈40/s in the container harness); a disabled replay endpoint returns 404; health telemetry emits `ONLINE` on `sensor.health.v1` |
| sensor-simulator client tests | ✅ Executed | REST client verified against a mock HTTP server (canonical JSON POSTed to `/ingest/detections` in batches); gRPC client verified against an in-process gRPC server implementing the real contract (streams records, totals acks) |
| Platform-wide 404 fix | ✅ Fixed | `BaseExceptionHandler` now renders `NoResourceFoundException` as 404 instead of the catch-all 500; unknown paths across all services report "not found" correctly |
| `buf lint` / `buf breaking` / `buf generate` (M5.1) | ✅ Executed | `track.proto` (`Track`, `TrackStatus`) added — lint clean, breaking clean (additive), Java/Python/TS regenerated |
| `mvn clean verify` (M5.1 track-correlation) | ✅ Executed | **17 modules, 352 tests** (+31), 0 failures — track-correlation: 27 unit + 4 Testcontainers integration |
| track-correlation integration tests | ✅ Executed | 4 against real PostgreSQL 17 + Kafka (Testcontainers) — a normalised detection becomes a tentative track published on `track.created.v1` and persisted; three nearby detections form one confirmed track with 3 states + 2 lifecycle events; a distant detection starts a separate track; the `GET /tracks` read API returns the track with `track:read`, 401 unauthenticated, 403 without the permission, 404 for an unknown id (MockMvc + injected JWT) |
| `Uuidv7Generator` → platform-commons | ✅ Refactored | Promoted from sensor-ingestion to `pk.nust.cuas.commons.id` and reused by track-correlation; the duplicate and its test moved, not copied |
| Testcontainers integration tests | ✅ Executed | 44 against real PostgreSQL 17 (audit, user, auth) — hash chain, DB-enforced immutability, concurrent append, self-escalation refusal, refresh-token reuse detection, fail-closed audit rollback. 46 against real Redis 7.4 (gateway) — token-bucket atomicity under concurrency, refill, fail-closed on an unreachable store, revocation propagation, the section 7.8 limits end to end |
| `mvn clean verify` (M5.2 + M5.3) | ✅ Executed | **17 modules, 379 tests** (+27), 0 failures — track-correlation: 52 unit + 6 Testcontainers integration |
| `TrackLifecycleIntegrationTest` | ✅ Executed | 2 against real PostgreSQL 17 + Kafka — a confirmed track unfed past the coast timeout is predicted forward to `COASTING` on `track.updated.v1`, then to `LOST` on `track.lost.v1`, with the full ordered `->TENTATIVE / TENTATIVE->CONFIRMED / CONFIRMED->COASTING / COASTING->LOST` chain persisted; and a detection reaching a coasting track revives it to `CONFIRMED` on the same `trackId` rather than starting a second track. Timeouts are widened via `@TestPropertySource` so the assertions observe ordered transitions rather than race Kafka delivery |
| `buf lint` / `buf breaking` / `buf generate` (M5.3) | ✅ Executed | `track.proto` amended with `KinematicEstimate`, `PredictedPoint`, `ClosestPointOfApproach`, `merged_into_track_id`, `TRACK_STATUS_MERGED` — lint clean, **breaking clean** (additive only, so a minor version under §5.2/§5.5 with no topic bump); Java/Python/TS regenerated |
| `mvn clean verify` (M5.4) | ✅ Executed | **17 modules, 397 tests** (+18), 0 failures — track-correlation: 70 unit + 6 Testcontainers integration |
| CPA + prediction end to end | ✅ Executed | `TrackLifecycleIntegrationTest` configures a protected asset through `correlation.assets` and asserts a coasting track reaches Kafka carrying a predicted trajectory with monotonically growing uncertainty, the named `nearest_asset_cpa`, and a positive `position_uncertainty_m` — filter to wire, via the `ProtectedAssetRegistry` port |
| `mvn clean verify` (M5 complete) | ✅ Executed | **17 modules, 448 tests**, 0 failures — track-correlation alone runs 127 |
| Cross-sensor merge end to end | ✅ Executed | `TrackMergeIntegrationTest` — two sensors 80 m apart open separate tracks (the association gate is shrunk below the merge gate, which is what makes the case reachable), the pair holds for three sweeps, the younger becomes `MERGED` with `merged_into_track_id` set, `track.merged.v1` carries both ids, and the lifecycle event plus evidence cleanup are persisted. A second test proves two tracks fed by the **same** sensor never merge |
| Redis hot state | ✅ Executed | `RedisTrackStateStoreIntegrationTest` against a real Redis 7.4 container — a full Kalman state round-trips bit-identically, the merged-survivor link survives, evict removes one field, the TTL is applied, and a corrupt entry reads as a miss. The other integration tests run with `correlation.hot-state.enabled=false`, exercising the no-Redis path |
| gRPC `SubscribeTracks` | ✅ Executed | `TrackStreamGrpcServiceTest` — all four event types and tags, sector filtering, empty filter meaning all sectors, a stalled subscriber skipped without starving others, a failed write detaching its subscriber, cancel+close detaching exactly once |
| `buf lint` / `buf breaking` / `buf generate` (M5.7) | ✅ Executed | `TrackService.SubscribeTracks` + `TrackEvent` added — lint clean, **breaking clean** (additive); Java/Python/TS regenerated |
| `mvn clean verify` (M6.1 orchestrator foundation) | ✅ Executed | **17 modules, 490 tests** (+42), 0 failures — ai-orchestrator: 30 tests (registry, modality routing, descriptor validation, boundary mapper, config binding, model-agnosticism guard) |
| Model-agnosticism guard | ✅ Executed | `ModelAgnosticismTest` scans `ai-orchestrator` source for the section 6.3 service and framework names, comments included, and fails the build on any hit. **Caught a real violation on its first run** — a javadoc in `ServiceId` used a live service name as an illustration; the prose was reworded rather than the check weakened |
| `mvn clean verify` (M6.2 mock provider) | ✅ Executed | **17 modules, 542 tests** (+52), 0 failures — ai-orchestrator: 84 |
| Docker engine outage during M6.2 | ⚠️ Environment, not a defect | A reactor run failed in `api-gateway` with `Could not find a valid Docker environment` after Docker Desktop stopped mid-session. Restarted headlessly (see above); the re-run passed every Testcontainers suite unchanged |
| `mvn clean verify` (M6.3 resilience) | ✅ Executed | ai-orchestrator: **96 tests** (+12) — breaker opens under injected/thrown transport failure, model errors never open it, bulkhead saturation degrades without opening the breaker, deadline enforced on a separate thread, listener failure cannot fail the inference, per-service isolation |
| `mvn clean verify` (M6.4 Kafka + cache) | ✅ Executed | ai-orchestrator: **135 tests** (+39) |
| Orchestrator Testcontainers suites | ✅ Executed | `RedisInferenceCacheIntegrationTest` (7) against real Redis 7.4 — a full protobuf response round-trips byte-identically, the service is part of the key, the TTL is applied and actually expires, a corrupt entry reads as a miss, and an unreachable Redis reads as a miss rather than throwing. `InferenceEventKafkaIntegrationTest` (4) against real Kafka — a routed request emits `requested` + `completed` keyed by `trackId` with the mock badge intact, modality routing is respected, an unroutable modality yields nothing, and an unusable outcome goes to `failed` rather than `completed` |
| `mvn clean verify` (M6.5 gRPC + canary + admin API) | ✅ Executed | ai-orchestrator: **153 tests** (+18) — canary splits 90/10 by track and holds an assignment for life, assignments are independent across services, and the admin API enforces `ai:configure` on both read and write with semantic-version and 0-100 validation |
| `mvn clean verify` (M6.6 Track A integration) | ✅ Executed | The orchestrator serves `Infer` on 9085; track-correlation calls it deadlined and circuit-broken behind a `TrackClassifier` port, consumes `ai.inference.completed.v1`, and publishes `track.classified.v1` |
| `buf lint` / `buf breaking` / `buf generate` (M6.6) | ✅ Executed | `TrackClassification` added to `track.proto` and set on `Track` — lint clean, **breaking clean** (additive) |
| `mvn clean verify` (M7.C alert-service) | ✅ Executed | **17 modules, 767 tests**, 0 failures — alert-service: 63 (a full-pipeline `@SpringBootTest` and a Redis leader-election test on Testcontainers PostgreSQL + Kafka + Redis, plus domain/policy/layering unit tests). Two defects fixed: nested Spring Data repositories never created (`considerNestedRepositories`), and a rollback-only transaction on the dedup-race insert (moved to `REQUIRES_NEW`) |
| `mvn test` (M7.D notification-service) + reactor `clean install -DskipTests` | ✅ Executed | notification-service: **54 tests, 0 failures** (a delivery/dedup/quiet-hours/retry `@SpringBootTest` on Testcontainers PostgreSQL, plus domain/policy/mapper/consumer/webhook/layering unit tests); the full 18-module reactor compiles and packages clean. Brings the suite to **821 tests across 18 modules** (767 green at M7.C + 54). One defect fixed: nested `@Entity` classes never registered in the Hibernate metamodel (`UnknownEntityException`), split into top-level entities |
| `buf lint` / `buf breaking` / `buf generate` (M8.A) | ✅ Executed | `config.proto` (ConfigUpdated + WatchConfig stream) and `report.proto` authored; lint clean, **breaking clean** (additive new files); §5.2 catalogue amended (32 → 37 primary topics) |
| `mvn test` (M8.B config-service) | ✅ Executed | config-service: **27 tests, 0 failures** (versioning/rollback/change-log-audit `@SpringBootTest` on Testcontainers PostgreSQL; a WatchConfig gRPC handler test; canonicalizer/authorizer/broadcaster/aggregate/layering unit tests). One fix: canonicalisation must read into generic Maps + `ORDER_MAP_ENTRIES_BY_KEYS` (a `JsonNode` serialises in insertion order, so `SORT_PROPERTIES_ALPHABETICALLY` does not sort its keys) |
| `npm ci` (frontend) | ⬜ Not yet | Frontend deferred to M9 |

### Testcontainers on Docker Engine 29

docker-java falls back to Docker API **1.32**, which Engine 29 rejects with an opaque HTTP 400
(`Could not find a valid Docker environment`, every strategy failing). The API version is pinned
to **1.41** via the surefire `api.version` system property in `platform-parent`, so integration
tests work without per-machine configuration.

Note it is the **system property `api.version`** that docker-java reads — the `DOCKER_API_VERSION`
environment variable is honoured by the `docker` CLI but *not* by docker-java, which is why
setting the env var alone does not fix it.

### Toolchain note

The machine used for M0 had no system JDK/Maven, so a **portable** Temurin JDK 21, Maven 3.9.9 and `buf` were unpacked under `Research Internship/.toolchain/` (outside the repo, git-ignored). A permanent system install of JDK 21 + Maven is the recommended setup and removes the need for this.

**None of the three is on `PATH` in a fresh shell**, and `buf` is needed by the `contracts` module's
`buf-generate` step, so a build that omits it fails at module 2 of 17. Export all three:

```bash
TC="$(pwd)/../.toolchain"          # from the repo root
export JAVA_HOME="$TC/jdk-21.0.11+10"
export PATH="$JAVA_HOME/bin:$TC/apache-maven-3.9.9/bin:$TC:$PATH"
```

> **Never pipe `mvn` into `tail`/`head` to shorten the output.** The pipeline's exit status is the
> *last* command's, so a missing `mvn` — or any build failure — is reported as exit 0 and reads as a
> green build. Redirect the full log to a file and grep it for `BUILD SUCCESS`. A stale
> `target/surefire-reports` from a previous run will otherwise supply a plausible, wrong test count.

Docker is a hard prerequisite for anything beyond a single-module `mvn verify` (the infra profile, integration tests using Testcontainers). It was not available in the M0 environment, so infra bring-up and Testcontainers-based integration tests are **specified but not yet observed** — the first milestone that runs on a Docker-equipped machine will verify them.

---

## First-time setup

```bash
cp .env.example .env
# fill in POSTGRES_PASSWORD, REDIS_PASSWORD, ELASTICSEARCH_PASSWORD, MINIO credentials
```

`.env` is gitignored and must never be committed (ARCHITECTURE.md §7.7).

---

## Backend

```bash
cd backend
mvn -B clean verify                       # all modules
mvn -B -pl track-correlation -am verify   # one module and its dependencies
```

Module layout:

- `platform-parent` — dependency and plugin management. Versions are pinned here and nowhere else.
- `platform-commons` — cross-cutting concerns shared by all twelve services.
- The twelve services listed in the root README.

---

## Frontend

```bash
cd frontend
npm ci
npm run dev          # http://localhost:3000
npm run typecheck
npm run test
npm run storybook    # design system — the §2.1 deliverable
```

---

## AI services

```bash
cd ai-services/rf-classifier
python -m venv .venv && source .venv/bin/activate   # Windows: .venv\Scripts\activate
pip install -r requirements.txt
pip install -r requirements-training.txt            # training only, never in the runtime image
pytest tests/
```

---

## Contracts

Protobuf definitions are the single source of truth for every wire format.

```bash
cd contracts
buf lint
buf breaking --against '.git#branch=main'
buf generate
```

`generated/` is build output. Never edit it, never commit it by hand.

---

## Full system with Docker

**Infrastructure acceptance test (one command).** Once Docker Desktop is running, this brings up
the infra profile, waits for health, applies the database and topic catalogues, and asserts all
ten databases, every topic in the §5.2 catalogue, and Schema Registry / Redis / MinIO /
Elasticsearch:

```bash
bash scripts/verify-infra.sh
```

Manual equivalents:

```bash
docker compose --profile infra up -d       # infrastructure only
docker compose --profile platform up -d    # + Track A services
docker compose --profile ai up -d          # + Track B AI services

# Databases are created automatically on first postgres init. Topics are declared explicitly:
docker compose exec kafka bash /scripts/create-topics.sh
```

The two-track switch (ARCHITECTURE.md §6.4):

```bash
AI_PROVIDER_MODE=mock docker compose --profile platform up -d
```

With `mock`, the entire platform runs end-to-end with zero trained models. Mocked results carry `model_version: "0.0.0-mock"` and `device: DEVICE_MOCK`, and the dashboard renders an explicit MOCK INFERENCE badge — mocked and real results are never confusable.

---

## Build order

Dependencies between the tracks mean one sensible order:

1. **`contracts/`** — nothing else can be generated until the protos exist
2. **`backend/platform-parent` → `platform-commons`** — every service depends on both
3. **Backend services** — independent of each other
4. **`frontend/`** — depends on generated TypeScript from `contracts/`
5. **`ai-services/`** — depends on generated Python from `contracts/`

---

## Where to start

`contracts/proto/detection.proto` and `contracts/proto/inference.proto` are empty and are the critical path. Both are specified in full in ARCHITECTURE.md §5.3 and §6.2 and can be lifted verbatim.

After those, in order: `infrastructure/kafka/create-topics.sh` (§5.2 catalogue — topic auto-creation is disabled by design), `infrastructure/postgres/init-databases.sh` (ten per-service schemas), then the design-system tokens in `frontend/src/design-system/tokens/` (§2.1).
