# Development

## Prerequisites

| Tool | Version | Purpose |
|---|---|---|
| JDK | 21 | Backend services |
| Maven | 3.9+ | Backend build |
| Node.js | 22+ | Frontend build |
| Python | 3.12+ | AI services |
| Docker | with Compose v2 | Full-system development |
| `buf` | latest | Protobuf lint, breaking-change detection, codegen |

---

## Verification status

**Important — read before following any command below.**

This scaffold was generated on a machine with Node 22, Python 3.14, and git available, but **without JDK or Maven installed**. Consequently:

| Command | Verified |
|---|---|
| `python scaffold` (structure generation) | ✅ Executed — 429 directories, 592 files |
| `mvn -B verify` | ❌ **Never executed.** Maven not installed on the generating machine. |
| `npm ci` | ❌ **Never executed.** Dependency versions are declared but unresolved. |
| `docker compose up` | ❌ **Never executed.** |

Every command in this document is the *intended* invocation derived from `ARCHITECTURE.md`, not a command observed to succeed. Expect to fix POM coordinates and dependency versions on first build. Correct this section as commands are verified.

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

```bash
docker compose --profile infra up -d       # infrastructure only
docker compose --profile platform up -d    # + Track A services
docker compose --profile ai up -d          # + Track B AI services
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
