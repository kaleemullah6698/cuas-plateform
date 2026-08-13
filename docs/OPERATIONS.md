# Operations Runbook

Operating the CUAS platform: starting it, checking it is actually working, backing it up, recovering
it, and diagnosing the failures that have actually happened rather than the ones that are easy to
imagine.

`docs/ARCHITECTURE.md` remains the source of truth for *why* anything is shaped the way it is. This
document is about *running* it. `docs/DEVELOPMENT.md` covers building and the local toolchain.

---

## 1. Starting the platform

```bash
COMPOSE_PARALLEL_LIMIT=2 docker compose --profile infra --profile platform up -d
docker compose exec kafka bash /scripts/create-topics.sh
```

`COMPOSE_PARALLEL_LIMIT=2` is not optional advice. Docker Desktop's Linux engine died twice during
M11 under concurrent image builds on a 7.4 GB engine, taking every container with it and returning
HTTP 500 from every API route. The `web` image (`npm ci` + `vite build`) is the heaviest single step.

**Before the first run**, copy `.env.example` to `.env` and set a bootstrap administrator. There is
deliberately no default password (§7.7) and auth-service refuses to start with the bootstrap enabled
and no password set.

### Readiness, and what "Up" does not mean

Every service reports a container-level health state derived from its own Spring readiness group:

```bash
docker compose ps
```

Wait for `(healthy)`, not `Up`. `Up` is true the instant the JVM starts — before Spring has a
context, before Flyway has migrated, and before any Kafka consumer has been assigned a partition.
Services carry `start_period: 120s`, so a container reporting `health: starting` for the first two
minutes is behaving normally.

Expected steady state: **16 healthy** — twelve Track A services plus postgres, kafka, redis and
schema-registry. `elasticsearch`, `kibana`, `minio` and `web` have no healthcheck and report `Up`.

---

## 2. Verifying it works

Three checks, increasing in strength. Run them in order; each assumes the one before it passed.

| Command | Proves |
|---|---|
| `bash scripts/verify-infra.sh` | Databases, topics, Redis, MinIO, Elasticsearch |
| `bash scripts/verify-platform.sh` | Every service answers; every gateway route is reachable; a replay becomes a track and an alert |
| `bash scripts/verify-journey.sh` | An operator can actually **use** it — real browser, real login, real WebSocket |

`verify-platform.sh` is deliberately **black box**: it talks only to the gateway on `:8080`, exactly
as the browser does. A check that reached past the gateway could pass while the thing an operator
uses was broken — which is precisely what M9 finding G-4 was.

`verify-journey.sh` drives the built SPA in Chromium with no mocked login and no mocked WebSocket.
For the strongest possible evidence, restart the gateway first:

```bash
docker compose restart api-gateway
bash scripts/verify-journey.sh
```

The gateway's fan-out consumer reads from `latest` and its track snapshot comes from an in-memory
view (§3.5, decision D-1), so after a restart its first `SNAPSHOT` is empty — and every track the
browser then receives demonstrably arrived from *that* run's replay, not from data left over.

---

## 3. Backup and restore

```bash
bash scripts/backup.sh                          # -> backups/<UTC timestamp>/
bash scripts/restore.sh backups/<timestamp>     # everything
bash scripts/restore.sh backups/<timestamp> alertdb   # one service
```

What is captured, and what deliberately is not:

- **Captured**: the ten per-service databases (one dump each) and the MinIO object store holding
  report artifacts and evidence bundles (§4.1.10).
- **Not captured**: Kafka's log, container images, and topic definitions. Kafka is a transport, not a
  system of record; images come from the Dockerfiles; topics come from `create-topics.sh`.

Each database is dumped separately on purpose. Database-per-service is absolute (§2), and a restore
that could only proceed all-or-nothing would make recovering one service impossible without touching
the other nine.

**Restores are destructive** — the dumps carry `--clean --if-exists`. Stop the platform services
first (`docker compose --profile platform stop`), restore, then start and verify.

### The check that matters after a restore

```bash
# POST, not GET — verification is a recorded operation, not a read.
curl -s -X POST -H "Authorization: Bearer $TOKEN" \
     http://localhost:8080/api/v1/audit/verify
```

A healthy chain answers:

```json
{"intact":true,"recordsVerified":109,"headHash":"27406a1f…","brokenAtSeq":null,"reason":null}
```

The audit log is a tamper-evident hash chain (§4.1.11). A restored `auditdb` that fails verification
means the backup is corrupt — and **nothing else in the platform will tell you that**. The UI renders
a broken chain as a full-width, non-dismissible critical banner naming the first broken link.

Verified after a real restore during M12: `intact: true`, 109 records, no broken link.

---

## 4. Restarting and rolling updates

Because services now have real healthchecks, a rolling restart is meaningful:

```bash
docker compose up -d --no-deps --force-recreate <service>
docker compose ps <service>          # wait for (healthy)
```

Two ordering facts worth knowing:

- **Recreating `api-gateway` needs nothing else recreated**, but it *clears the live view*: its
  in-memory track snapshot is empty afterwards and its consumer resumes from `latest`. Operators will
  see an empty console until new events arrive. This is by design (§3.5, D-1), not a fault.
- **Do not recreate `api-gateway` and expect `web` to follow it automatically.** nginx used to
  resolve its upstream once at startup and hold the address; that is fixed (it re-resolves per
  request with a 10 s TTL), but if you see `502` on `/api` or `/ws` while the gateway is healthy,
  this is the first thing to check.

---

## 5. Failures that have actually happened

Each of these was found by running the platform, and each looked like something else first.

| Symptom | Actual cause |
|---|---|
| Sign-in returns **500**, always | auth-service cannot reach user-service's gRPC `ResolvePermissions`, which builds the JWT's `perms` claim. Check `USER_SERVICE_HOST`. |
| Sign-in returns **500**, only the first time after a restart | The cold `ResolvePermissions` call exceeds its deadline while user-service class-loads. `USER_SERVICE_DEADLINE_MS`. |
| Sign-in returns **401** with correct credentials | Was the rate limiter surfacing as 401 because the gateway's Kafka producer could not be constructed. Now returns a proper **429**. |
| Sign-in returns **429** | Working as designed. `LOGIN_PER_ACCOUNT` is 10/hour, burst 3 (§7.8), refilling ~1 token per 6 min. **Do not probe the endpoint to check** — the probe is itself a login and consumes the token you are waiting for. |
| Console shows "Sign-in failed", gateway healthy | nginx in `web` proxying a stale gateway address. `docker compose logs web`; expect 502s. |
| Replay reports `accepted 300, rejected 0` but **no tracks appear** | The intake deduplicates on `(sensorId, sensorSeq)` so redelivery is safe (§4.1.4). A replay with the same ids and sequences restarting at 1 *is* a redelivery and is dropped at normalisation, at DEBUG. Set `SIMULATOR_RUN_LABEL` per replay. |
| Tracks appear but **class and confidence read `——`** | track-correlation's circuit breaker is open. The mock provider fits its p95 *to* each configured timeout (§6.4) and sleeps for the sample, so a full-burst replay overruns the `PT0.5S` Infer budget. This is §6.5's specified degradation: tracks, scores and alerts are unaffected. |
| **No tracks at all** from a thinned replay | Fewer detections than `required-consecutive-sweeps` — correlation never promotes a tentative track. Thinning the replay is not a fix for the previous row. |
| `pg_dump` / `psql` fail with **"too many clients already"** | Was eleven services each holding HikariCP's default pool of 10 against `max_connections=100`. Pools are capped and the ceiling raised; if it recurs, check `SELECT count(*) FROM pg_stat_activity`. |
| Every Docker API route returns **500** | The Docker Desktop engine has died, usually under concurrent builds. `docker desktop restart`, then bring the stack back with `COMPOSE_PARALLEL_LIMIT=2`. |

### Reading the logs

```bash
docker compose logs <service> --since 5m | grep -iE "ERROR|WARN|Caused by"
```

Most of the failures above produced **no ERROR line at all** — they were silent, with every container
up and every probe green. When something is not happening and nothing is complaining, look at the
Kafka consumer positions before looking at the services:

```bash
docker compose exec kafka kafka-consumer-groups --bootstrap-server localhost:9092 --describe --all-groups
```

Zero lag with no output is the signature of a message that was accepted and then dropped downstream —
which is what the intake deduplication does by design.

---

## 6. Observability

```bash
docker compose --profile observability up -d
```

Prometheus, Grafana, Loki and Tempo, with scrape config, alert rules, datasources and the four §8.6
dashboards provisioned as version-controlled IaC under `infrastructure/monitoring/`.

The alert rule to take most seriously is the **audit chain break** page. Every other alert describes
degraded service; that one describes evidence that can no longer be trusted.

---

## 7. Scope limit — engagement

The platform produces **advisory recommendations only**. RF jamming, GNSS spoofing and cyber takeover
are outside the software boundary, and every recommendation terminates at an operator authorisation
gate.

This is enforced by test, not merely by documentation: `Recommendation` must hold exactly the five
§6.3.4 values and `EngagementAction` exactly `ENGAGEMENT_ACTION_NONE`, and the build fails if a value
is added. If an operator or reviewer asks whether the system can be made to act on a track, the
answer is no, and the contract will not compile if someone tries.
