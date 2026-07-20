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
| `create-topics.sh` live against Kafka | ✅ Executed | **24 topics created** (18 primary + 6 DLQ); partitions/retention/cleanup match §5.2; idempotent on re-run |
| `scripts/verify-infra.sh` (DBs + topics + health) | ✅ Executed | **33 passed, 0 failed** |
| Kafka produce/consume round-trip | ✅ Executed | Message produced and consumed; host listener `localhost:29092` correctly advertised |
| Host reachability of all mapped ports | ✅ Executed | 5432, 29092, 8081, 9200, 6379, 9000, 9090, 5601 all reachable |
| `mvn clean verify` (M2.1) | ✅ Executed | 16 modules, **97 tests**, 0 failures |
| Testcontainers integration tests | ✅ Executed | M2.1 — 12 tests against real PostgreSQL 17 (Flyway migration, hash chain, DB-enforced immutability, concurrent append) |
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

The machine used for M0 had no system JDK/Maven, so a **portable** Temurin JDK 21 and Maven 3.9.9 were unpacked under `Research Internship/.toolchain/` (outside the repo, git-ignored). To reproduce the M0 build there, point `JAVA_HOME` at that JDK and prepend both `bin` directories to `PATH`. A permanent system install of JDK 21 + Maven is the recommended setup and removes the need for this.

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
ten databases, all twenty-four topics, and Schema Registry / Redis / MinIO / Elasticsearch:

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
