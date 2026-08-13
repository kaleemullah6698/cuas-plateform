# Supervisor Demo Runbook

A reproducible demonstration of the platform running for real: a browser talking to the operator
console, the console talking to the API gateway, and a continuously simulated airspace travelling the
whole pipeline into live tracks and alerts.

**Nothing here is mocked.** No fixture data, no demo mode, no stubbed WebSocket, no Playwright route
interception. Every track and alert on screen came out of Kafka.

> **Status: locally verified. Not production-ready** — see §I.

---

## A. Startup

### Prerequisites

- **Docker Desktop running.** This is a 20-container stack on a 7.4 GB engine; it is tight.
- **A `.env`** in the repository root:
  ```
  CUAS_BOOTSTRAP_ADMIN_ENABLED=true
  CUAS_BOOTSTRAP_ADMIN_USERNAME=admin
  CUAS_BOOTSTRAP_ADMIN_PASSWORD=<at least 12 characters>
  POSTGRES_PASSWORD=<anything>
  REDIS_PASSWORD=<anything>
  ```
  There is deliberately no default password (§7.7); auth-service refuses to start without one.
- **Images already built** — `docker images | grep -c cuas-platform` should print `14`.

### Commands

```bash
cd cuas-platform

# 1. Start the platform. COMPOSE_PARALLEL_LIMIT=2 is not optional on this host.
COMPOSE_PARALLEL_LIMIT=2 docker compose --profile infra --profile platform up -d

# 2. Free memory: Elasticsearch + Kibana use ~1.5 GB and are on NO demo path.
docker compose stop elasticsearch kibana

# 3. Wait for health — NOT for "Up", which is true the moment a JVM starts.
docker compose ps
```

**Expect 16 `(healthy)`.** Allow ~2 minutes; services carry a 120 s `start_period`. `minio`,
`schema-registry` and `web` show `Up` (no healthcheck) — that is correct.

First start on a fresh database only:
```bash
docker compose exec kafka bash /scripts/create-topics.sh
```

---

## B. Login

**http://localhost:3000** — sign in as **`admin`** with the password from `.env`.

> ### Warm the login up before your supervisor sits down
>
> The **first** sign-in after the stack starts reliably fails with a **500**. auth-service's first
> `ResolvePermissions` gRPC call pays user-service's class-loading and exceeds its deadline.
> Measured: **500 in 22.6 s cold, then 200 in 1.15 s warm.**
>
> So: sign in once yourself (expect the failure), sign in again (it works), then leave it alone.
>
> **Then sign in sparingly.** The gateway limits logins to 10/hour per account (burst 3) and
> 5/minute per IP (§7.8). Further attempts return **429** — that is the control working, not a bug.
> Do not "test" it beforehand; each test spends a token you will want live.

`admin` holds the ADMIN role and therefore every §7.4 permission, so all navigation is available.
There is exactly one seeded account — no self-service registration, by design.

---

## C. The simulator — run this for the whole demonstration

The simulator is a **client**, not a service, and sensor-ingestion publishes its intake on `8083`.
Running it from the host avoids an in-container Maven build, which is the heaviest operation in this
repository and has repeatedly exhausted the Docker engine.

```bash
../.toolchain/jdk-21.0.11+10/bin/java \
  -Dsimulator.rest-base-url=http://localhost:8083 \
  -Dsimulator.transport=REST -Dsimulator.sensors=3 \
  -Dsimulator.detections-per-sensor=6 -Dsimulator.interval=PT8S \
  -Dsimulator.run-on-startup=true -Dsimulator.continuous=true \
  -jar backend/sensor-simulator/target/sensor-simulator-0.1.0-SNAPSHOT.jar
```

Leave it running. Stop with `Ctrl-C`. If the jar is missing, build it on the host (no Docker):

```bash
JAVA_HOME=../.toolchain/jdk-21.0.11+10 \
  ../.toolchain/apache-maven-3.9.9/bin/mvn -f backend/pom.xml -pl sensor-simulator \
  package -DskipTests -Dbuf.skip=true
```

**Why these numbers.** 18 detections every 8 s (~135/minute) keeps every sensor inside
`online-within` (30 s) and every track inside `coast-timeout` (10 s), while leaving the host
headroom. A faster rate was measured at ~430/minute and made sign-in take 11.5 s under contention.

**Why continuous rather than a one-shot replay.** A single run is a burst — 300 detections in four
seconds, then exit. `lost-timeout` is 30 s and alerts auto-resolve with their track, so ~45 s later
the console correctly shows zeros. Continuous emission keeps the sensor ids fixed and the sequence
numbers climbing, which is what a real sensor does, and needs no run label because a rising sequence
never collides with the intake's `(sensorId, sensorSeq)` dedup window.

---

## D & E. What to demonstrate, and what each page proves

| Page | What to show | The real backend evidence behind it |
|---|---|---|
| **Dashboard** | Non-zero **ACTIVE TRACKS**, **LIVE ALERTS**, **SENSORS ONLINE**, **HIGHEST THREAT** | Gateway's live view, fed by `track.*` / `alert.raised` / `sensor.health` Kafka topics over the WebSocket |
| **Tracks** | Threat-sorted triage queue; `J`/`K` to move, `Enter` to select. Right panel: identity, telemetry, velocity/altitude sparklines | `track.created` / `track.updated` from track-correlation, ~45/min. Sparklines are accumulated client-side from observed values — not fabricated history |
| **Live Map** | Tracks plotted with heading vectors and trails, styled by severity; critical/high never clustered | Same live track stream, pushed to MapLibre at the §2.5.1 10 Hz budget |
| **Alerts** | Three buckets; real `ALT-nnnn` references; open one | `alert.raised` from alert-service, ~45/min, raised when a threat score crosses §6.3.4's MEDIUM floor |
| **Incidents** | Declare an incident from an alert; timeline, notes, evidence, decision chain | alert-service incident API |
| **User Management** | Try granting yourself a role you already hold → server refuses **403 self-escalation** | user-service, server-side §7.4 enforcement — the UI merely surfaces the refusal |
| **Audit** | Query the log, then **Verify** the hash chain → `intact` | audit-service tamper-evident chain (§4.1.11). A broken chain renders a full-width critical banner |
| **Reports** | Template list, generate → poll → download | reporting-service async job engine |

### The scope limit — say this out loud

Open an incident's **Decision Chain**: engagement reads **`NONE — outside system scope`**. That is
structural. `Recommendation` holds exactly five values and `EngagementAction` exactly
`ENGAGEMENT_ACTION_NONE`, enforced by a test that fails the build if a value is added. The system
cannot be made to act on a track.

---

## F. Demonstrating a live alert

1. Sit on **Tracks** with the simulator running. Tracks update continuously — no refresh, no polling.
2. Note a track id near the top of the queue.
3. Switch to **Alerts**. New alerts arrive continuously with real `ALT-` references.
4. Open one; then open its source track.
5. Point out that the alert's `trackId` is a track that was in the live queue.

The full path: `simulator → sensor-ingestion → Kafka → track-correlation → ai-orchestrator (mock
inference) → Kafka → threat-assessment → Kafka → alert-service → Kafka → api-gateway → WebSocket →
browser`.

---

## G. WebSocket connection status

The header shows **`LIVE`**; the Track Console header shows **`Live`** with an `n/m tracks` counter
that changes as data arrives. That counter *is* the socket — it is not polled.

---

## H. Gateway interruption and recovery

Safe, and worth showing — it proves the console is genuinely socket-driven.

```bash
docker compose stop api-gateway     # console shows the connection-lost banner, tracks desaturate
docker compose start api-gateway    # reconnects automatically
```

**Two things to know before doing this in front of someone.**

1. After restart the gateway's live view starts **empty** (it reads from `latest` and its snapshot is
   in-memory, §3.5 decision D-1). With the simulator running it repopulates within seconds — but say
   so, or the brief empty console looks like a failure.
2. Your session survives, but if you sign in again afterwards you spend a rate-limit token.

Allow ~60 s for the gateway to become healthy again.

---

## I. Known limitations — do not claim these are complete

**The system is locally verified, not production-ready.** Do not describe it otherwise.

- **No mTLS between services.** Every inter-service gRPC hop is plaintext (SR-2).
- **No secrets backend.** Credentials come from `.env` (§7.7).
- **No load or stress validation.** The §1.x track ceilings are unmeasured; there is no 10k-track soak.
- **No Postgres RLS (SR-3), no Elasticsearch ILM (SR-4).**
- **No failover or DR beyond backup/restore.** Single-node Postgres, single-broker Kafka.
- **Nothing deploys.** CI builds, scans and signs images; the deploy half of §8.4 does not exist.
- **Track B AI services are empty scaffolds.** `ai-services/*/src/**` contains only `__init__.py`;
  the working provider is the **Java** `MockInferenceProvider` inside ai-orchestrator. Classifications
  shown are mock inference, and the console labels them as such.
- **`threat-assessment` has an open duplicate-key race** — concurrent `track.updated` for one track
  produces `duplicate key ... threat_scores_pkey`. It self-heals (the consumer retries and scores keep
  flowing) but that write should be an upsert. **Found and documented, not fixed** — the fix needs an
  image rebuild, and rebuilds repeatedly killed the engine.
- **The `sensor-simulator` image is stale.** Its rebuild is what killed the engine; the host-JAR path
  in §C runs the same fixed code.
- **Map layers deferred**: protected zones, sensor geometry, predicted trajectories, self-hosted
  tiles. They are present but disabled with their reason shown — not faked.
- **Configuration → `threat-policy` shows 404.** The route reaches config-service correctly; no policy
  namespace has been created. An empty state, not a broken route.
- **Class/confidence may read `——`** if the inference breaker opens under load. §6.5's specified
  degradation: tracks, scores and alerts are unaffected.

---

## Troubleshooting

| Symptom | Cause and fix |
|---|---|
| Every `docker` command returns **500** | The Docker engine has died — it reports zero containers *and* zero images, which looks like everything was deleted. **Nothing is lost.** `docker desktop restart`, wait ~2 min. |
| First sign-in **500** | Cold-start gRPC deadline. Retry once — see §B. |
| Sign-in **429** | Rate limit. Wait ~6 min. Do not probe; the probe is itself a login. |
| Dashboard all zeros | The simulator is not running, or it is a one-shot burst that has aged out. Start §C. |
| Console "Sign-in failed", gateway healthy | nginx proxying a stale gateway address: `docker compose restart web`. |
| Simulator prints `WARN ... continuing` | A transient intake/Kafka failure. By design it keeps going; no action needed. |
| Engine unstable | Stop `elasticsearch` and `kibana`; do **not** rebuild images during a demo. |

## Rehearsal checklist

1. `docker compose ps` → 16 healthy
2. `docker compose stop elasticsearch kibana`
3. Start the §C simulator; leave it running
4. Warm the login (§B): fail once, succeed once
5. Confirm Dashboard counters non-zero and Alerts filling
6. **Stop touching it.**

## Shutdown

```bash
# Ctrl-C the simulator first, then:
docker compose --profile infra --profile platform stop      # keeps data
docker compose --profile infra --profile platform down -v   # deletes ALL data incl. the admin account
```

Use `stop` between rehearsals. `down -v` destroys the bootstrap administrator and the audit chain.
