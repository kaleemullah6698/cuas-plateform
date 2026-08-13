# Supervisor Demo Runbook

A reproducible fifteen-minute demonstration of the platform running for real: a browser talking to
the operator console, the console talking to the API gateway, and a replayed intrusion travelling the
whole pipeline into a live alert.

**Nothing in this demonstration is mocked.** No fixture data, no demo mode, no stubbed WebSocket.
Every track and alert on screen came out of Kafka.

---

## 1. Prerequisites

- **Docker Desktop running.** The stack is 20 containers; give the engine at least 8 GB.
- **A `.env` file** in the repository root with the bootstrap administrator:
  ```
  CUAS_BOOTSTRAP_ADMIN_ENABLED=true
  CUAS_BOOTSTRAP_ADMIN_USERNAME=admin
  CUAS_BOOTSTRAP_ADMIN_PASSWORD=<at least 12 characters>
  POSTGRES_PASSWORD=<anything>
  REDIS_PASSWORD=<anything>
  ```
  There is deliberately no default password (§7.7); auth-service refuses to start without one.
- **Images already built.** If `docker images | grep cuas-platform` shows 14, skip the build. If not,
  see *Troubleshooting → First run*.

---

## 2. Start the platform

```bash
cd cuas-platform
COMPOSE_PARALLEL_LIMIT=2 docker compose --profile infra --profile platform up -d
```

`COMPOSE_PARALLEL_LIMIT=2` is not optional. The engine has died twice under concurrent builds.

Wait for health — **not** for "Up", which is true the moment a JVM starts:

```bash
docker compose ps
```

**Expect 16 `(healthy)`**: the twelve services plus postgres, kafka, redis and schema-registry.
`elasticsearch`, `kibana`, `minio` and `web` have no healthcheck and correctly show `Up`.
Allow about two minutes; services carry a 120 s `start_period`.

If this is the first start on a fresh database, apply the topic catalogue once:

```bash
docker compose exec kafka bash /scripts/create-topics.sh
```

---

## 3. Open the console

**http://localhost:3000**

Sign in with the bootstrap administrator from `.env` (`admin` / your password). The account holds the
ADMIN role and therefore every §7.4 permission, so all navigation is available.

> **Warm the login up before your supervisor sits down.** The *first* sign-in after the stack starts
> reliably fails with a **500**: auth-service's first `ResolvePermissions` gRPC call pays
> user-service's class-loading and exceeds its deadline. Measured here: **500 in 22.6 s cold, then
> 200 in 1.15 s warm**. Sign in once yourself, confirm it fails, sign in again, confirm it works —
> then leave the session alone.
>
> **Then sign in sparingly.** The gateway rate-limits logins to 10/hour per account with a burst of 3,
> and 5/minute per IP (§7.8). Repeated attempts produce a **429**. That is the control working — do
> not "test" it beforehand, because each test consumes a token you will want live.

---

## 4. Bring the airspace to life

### Use this for the demonstration — continuous emission from the host

The simulator is a **client**, not a service, and sensor-ingestion publishes its intake on `8083`
(REST) and `9083` (gRPC). Running it from the host is the recommended path on a memory-constrained
machine: it avoids an in-container Maven build, which is the single heaviest operation in this
repository and the one that has repeatedly exhausted the Docker engine.

```bash
# Once, if the jar is not current (host build, no Docker involved):
JAVA_HOME=../.toolchain/jdk-21.0.11+10 \
  ../.toolchain/apache-maven-3.9.9/bin/mvn -f backend/pom.xml -pl sensor-simulator package -DskipTests -Dbuf.skip=true

# Then, for the demonstration — leave this running:
../.toolchain/jdk-21.0.11+10/bin/java \
  -Dsimulator.rest-base-url=http://localhost:8083 \
  -Dsimulator.transport=REST -Dsimulator.sensors=3 \
  -Dsimulator.detections-per-sensor=6 -Dsimulator.interval=PT8S \
  -Dsimulator.run-on-startup=true -Dsimulator.continuous=true \
  -jar backend/sensor-simulator/target/sensor-simulator-0.1.0-SNAPSHOT.jar
```

Sensors stay **ONLINE**, tracks stay live and alerts keep arriving, because the sensors keep
reporting — exactly as real ones would. Stop it with `Ctrl-C`.

**These numbers are deliberate.** 18 detections every 8 s (~135/minute) keeps every sensor inside
`online-within` (30 s) and every track inside `coast-timeout` (10 s) while leaving the host headroom.
A faster rate was measured at ~430/minute and made sign-in take 11.5 s under contention.

If your machine has more memory, the containerised equivalent is
`SIMULATOR_CONTINUOUS=true docker compose --profile sim --profile platform up -d sensor-simulator`,
which requires the `sensor-simulator` image to be built first.

### Free memory before demonstrating

Elasticsearch and Kibana consume ~1.5 GB and are on **no** demo path. Stopping them measurably
improved stability here:

```bash
docker compose stop elasticsearch kibana
```

**Why this mode exists, and it is worth understanding before you present.** A one-shot replay is a
*burst*: 300 detections in about four seconds, then the process exits. Three timeouts then take
effect —

| Setting | Value |
|---|---|
| `track-correlation.coast-timeout` | 10 s |
| `track-correlation.lost-timeout` | **30 s** |
| `sensor-ingestion.health.online-within` | **30 s** |

— so roughly forty-five seconds after a burst, every track has been dropped as lost, every alert has
auto-resolved with its track, and every sensor is reported OFFLINE. The console then correctly shows
`ACTIVE TRACKS 0`, `SENSORS ONLINE 0/n`, and "airspace clear". **That is the pipeline working, not
failing** — a burst input simply cannot populate a live display.

Continuous mode keeps the sensor ids fixed and the sequence numbers climbing, which is what a real
sensor does. It needs no run label, because a rising sequence never collides with the intake's
`(sensorId, sensorSeq)` dedup window.

### One-shot replay — for showing a single intrusion arriving

```bash
SIMULATOR_RUN_LABEL=$(date +%s) \
  docker compose --profile sim --profile platform run --rm --no-deps sensor-simulator
```

Useful to show tracks and an alert appearing *while your supervisor watches*, but expect them to age
out within about a minute. **`SIMULATOR_RUN_LABEL` is required here**: the intake deduplicates on
`(sensorId, sensorSeq)` so at-least-once redelivery is safe (§4.1.4), and without a fresh label a
second replay is indistinguishable from a redelivery of the first and is discarded in full — every
detection reported "accepted", and no track ever appearing.

---

## 5. What to show, and what your supervisor should see

Work down the left navigation. Every screen below is backed by a live service.

| Screen | What to point at |
|---|---|
| **Dashboard** | Live counts and system status, driven by the same WebSocket as everything else |
| **Tracks** | The triage queue, threat-sorted — most dangerous always on top. Press `J`/`K` to move, `Enter` to select. The right panel shows identity, telemetry, and client-accumulated velocity/altitude sparklines built from observed data |
| **Live Map** | Tracks plotted with heading vectors and accumulated trails, styled by severity. Critical and high are never clustered (§2.5.4) |
| **Alerts** | Three buckets — Unacknowledged / In Progress / Resolved — filled live from the raised-alert feed. Alerts carry real `ALT-nnnn` references assigned by alert-service |
| **Incidents** | Declare an incident from an alert; tabbed timeline, notes, evidence, decision chain |
| **User Management** | Accounts, roles and effective permissions. Try granting yourself a role you already hold — the server refuses with a **403 self-escalation** error, surfaced in the UI |
| **Audit** | Query the tamper-evident log, then **Verify** the hash chain. A healthy chain reports `intact`; a broken one renders a full-width, non-dismissible critical banner naming the first broken link |
| **Reports** | Templates list, async generate → poll → download |

**Connection state** is always visible: the header shows `LIVE`, and the Track Console header shows
`Live` with an `n/m tracks` counter. Stop the gateway (`docker compose stop api-gateway`) and the
console degrades to the §2.5.2 connection-lost banner with desaturated tracks, then recovers when you
start it again. This is worth showing — it demonstrates the console is genuinely driven by the socket.

### The headline moment — intrusion to alert

1. Sit on **Tracks** with the console connected.
2. Run the replay command from §4.
3. Tracks appear and update live — no page refresh, no polling.
4. Switch to **Alerts**: a real alert has been raised for a track that crossed the §6.3.4 MEDIUM
   threshold.
5. Open it, then open the source track. The alert's `trackId` is the track you were just watching.

That path is: `simulator → sensor-ingestion → Kafka → track-correlation → ai-orchestrator (mock
inference) → Kafka → threat-assessment → Kafka → alert-service → Kafka → api-gateway → WebSocket →
browser`.

### The scope limit — worth saying out loud

Open an incident's **Decision Chain**. The engagement action reads **`NONE — outside system scope`**.
That is structural, not cosmetic: the platform emits advisory recommendations only, and the contract
has no field that could say anything else. `Recommendation` must hold exactly five values and
`EngagementAction` exactly `ENGAGEMENT_ACTION_NONE`, enforced by a test that fails the build if a
value is added.

---

## 6. Known states that are not faults

Say these before your supervisor spots them.

- **Class and confidence show `——` after a full-burst replay.** The mock inference provider samples a
  lognormal fitted so its p95 lands *exactly* on each service's configured timeout (§6.4) —
  deliberately, so the circuit breaker is exercised — and sleeps for the sample. 300 detections in a
  few seconds push track-correlation past its `PT0.5S` inference budget and its breaker opens. Tracks
  stay unclassified while it is open. This is §6.5's specified degradation: **tracks, threat scores
  and alerts are unaffected**, the pipeline continues without the model rather than stalling on it.
- **Configuration shows nothing for `threat-policy`.** The route reaches config-service correctly; no
  policy namespace has been created yet, so it answers 404. An empty state, not a broken route.
- **Map layers for protected zones, sensor positions and predicted trajectories are present but
  disabled**, labelled with their reason. Their authoritative data sources are documented deferrals —
  they are not faked (see `docs/ROADMAP.md`).
- **Sensors may show OFFLINE** from earlier replays. Each labelled run creates distinct sensor ids;
  old ones age out.

---

## 7. Shutdown

```bash
docker compose --profile infra --profile platform stop      # keeps data
docker compose --profile infra --profile platform down      # removes containers, keeps volumes
docker compose --profile infra --profile platform down -v   # also deletes all data
```

Use `stop` between rehearsals — `down -v` destroys the bootstrap administrator and the audit chain.

---

## 8. Troubleshooting

| Symptom | Cause and fix |
|---|---|
| Every `docker` command returns **500 Internal Server Error** | The Docker Desktop Linux engine has died — it will also report zero containers *and* zero images, which looks like everything was deleted. Nothing is lost. `docker desktop restart`, wait ~2 min; containers return automatically. |
| Sign-in returns **429** | Login rate limit (10/hour, burst 3). Wait ~6 min for a token. Do not probe the endpoint — the probe is itself a login. |
| Sign-in returns **500** on the very first attempt after a start | Cold `ResolvePermissions` gRPC call exceeding its deadline while user-service class-loads. Retry once; it succeeds warm. |
| Console shows "Sign-in failed", gateway healthy | nginx in `web` proxying a stale gateway address. `docker compose restart web`. |
| Dashboard shows **0 tracks, 0 alerts, 0/n sensors** and "airspace clear" | Nothing is broken. The last replay was a burst and everything has aged out past the 30 s lost-timeout / online-within. Start continuous emission (§4). The REST history is intact — the dashboard shows the *live* view, which is the §3.5 design. |
| Replay reports "accepted 300" but **no tracks appear** | Missing or reused `SIMULATOR_RUN_LABEL` on a **one-shot** replay — deduplicated as a redelivery. Re-run with `$(date +%s)`, or use continuous mode, which needs no label. |
| **No tracks at all**, and the label was fresh | Fewer detections than `required-consecutive-sweeps`; correlation never promotes a tentative track. Use the default scenario size — do not thin it. |
| Services stuck at `health: starting` | Normal for up to 120 s. Beyond that: `docker compose logs <service> --since 5m`. |
| `pg_dump`/`psql` refuse with "too many clients" | Should not recur — pools are capped and the ceiling raised. Check `SELECT count(*) FROM pg_stat_activity`. |
| **First run**, no images | `cd contracts && buf generate && cd ..` then `COMPOSE_PARALLEL_LIMIT=2 docker compose --profile infra --profile platform up -d --build`. Allow 30–50 minutes. `contracts/generated` is git-ignored, so the `buf generate` step is required. |

---

## 9. Rehearsal checklist

Fifteen minutes before, in this order:

1. `docker compose ps` → **16 healthy**
2. `curl -s -o /dev/null -w "%{http_code}\n" http://localhost:3000/` → **200**
3. Start **continuous** emission (§4) and leave it running
4. Confirm the dashboard shows non-zero **ACTIVE TRACKS** and **SENSORS ONLINE**
5. Confirm an alert appears in **Alerts**
6. **Then stop touching it.** Every extra sign-in spends a rate-limit token.

For a fully automated end-to-end check — real browser, real login, real WebSocket, no mocks:

```bash
bash scripts/verify-journey.sh
```

It restarts the gateway first so its snapshot is empty, which proves that every track the browser
receives arrived from that run's replay rather than from data left over.
