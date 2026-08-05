# track-correlation

Normalised detections associated into tracks.

- **Ports:** 8084 (HTTP), 9084 (gRPC)
- **Database:** `trackdb` (`tracks`, `track_states`, `track_detections`, `track_lifecycle_events`, `track_merge_candidates`); Redis for hot in-flight state
- **Specification:** `docs/ARCHITECTURE.md` sections 4.1.5, 5.3.2

## The pipeline

```
sensor.detection.normalized.v1 ─▶ (consume)
     place in sector ─ predict each candidate to the detection's time ─ gate ─ nearest-neighbour
          match ─▶ Kalman predict-then-update, extend track ─▶ track.updated.v1
          no match ─▶ open a new tentative track            ─▶ track.created.v1

timeout sweep (every lifecycle.check-interval)
     confirmed, unfed past coast-timeout ─▶ predict forward, COASTING ─▶ track.updated.v1
     open,      unfed past lost-timeout  ─▶ LOST (terminal)           ─▶ track.lost.v1

merge sweep (every merge.check-interval)
     two confirmed tracks, one sector, disjoint sensors, inside the gate for N sweeps
                                         ─▶ younger MERGED (terminal) ─▶ track.merged.v1
```

Every published track also goes to the in-process gRPC `SubscribeTracks` subscribers.

A detection is placed in a geographic sector and compared against the open tracks recently seen
there. Each candidate is first *predicted* by its own Kalman filter to the detection's time, and the
detection extends the nearest track whose predicted position falls inside the distance gate;
matching none, it opens a new tentative track. A track confirms once enough detections have
associated to it. The aggregate and its full history — every state, association, and lifecycle
transition — are persisted, and the track is published on `track.created.v1` / `track.updated.v1` /
`track.lost.v1` / `track.merged.v1`.

## What to know before changing this service

**Association gates on the prediction, not the last fix.** Each candidate is offered to the policy
with the position its filter predicts for the detection's time. That is what lets a fast-moving
track keep associating, and lets a coasting track be re-acquired where it is *expected* to be. There
is no separate time gate: the temporal bound is applied by the candidate query, which never offers a
track older than the lost timeout.

**A track carries two times, and they diverge while coasting.** `last_seen` / `lastDetectionAt` is
when a detection last fed the track and is what the miss timeouts count from; `observed_at` /
`estimate().observedAt()` is the time the current estimate is valid for, which a coasting prediction
advances past `last_seen`. **Anything that advances the filter must measure `dt` from `observed_at`**
— measuring from `last_seen` re-advances a coasting track's filter over an interval it has already
covered, and silently corrupts the estimate on revival.

**It runs single-threaded, on purpose.** The consumer's concurrency is one, which is what lets the
read-modify-write on a track be a plain transaction. The timeout sweep runs alongside it, so each
swept track transitions in its own transaction under an optimistic lock (`@Version`); a track that a
detection changed concurrently simply loses the race and is retried on the next tick — as does the
merge sweep, which runs on its own cadence. The sector is assigned and association is scoped to it;
**exclusive per-instance sector ownership and concurrent per-sector processing are not yet built**,
and are what the service would need before it could be scaled out horizontally.

**The outlook is derived, never stored.** A published track carries where it is going
(`predicted_trajectory`) and what it is closing on (`nearest_asset_cpa`). Both are recomputed from
the filter on every publication by `TrackOutlookCalculator` and never persisted — storing them would
create a staler second copy of something derivable in microseconds, and would need invalidating
every time the asset registry changed. Only `CONFIRMED` and `COASTING` tracks get one: a tentative
track may be a sensor artefact, and putting a confident trajectory and a CPA countdown on screen for
something that may not exist is worse than showing nothing.

**CPA is closed-form and horizontal.** It solves `t = -(r·v)/|v|²` directly rather than scanning the
predicted trajectory for its nearest point — a scan would be bounded by the prediction horizon and
quantised by its step, and would report a CPA at the horizon's edge for anything approaching more
slowly than that. Altitude is deliberately excluded: folding it in would make a high overflight of
an asset read as distant when the ground track passes directly over it.

**Protected assets arrive through a port.** `ProtectedAssetRegistry` is declared in `domain/port/`;
today's adapter reads `correlation.assets` from configuration. §4.1.12 assigns asset geometry to the
Configuration Service, so at M8 the adapter is replaced by a config-service-backed one refreshed via
`WatchConfig` — an infrastructure swap with no domain, CPA, or contract change.

**A merge needs evidence, not a coincidence.** Two confirmed tracks in one sector, fed by *disjoint*
sensor sets, whose estimates stay inside the merge gate for N consecutive sweeps, collapse into one:
the older survives (UUIDv7 is time-ordered, so "older" is the lower id and every instance would
decide identically), the younger becomes `MERGED` with `merged_into_track_id` set, and
`track.merged.v1` carries both ids so consumers can re-key. The N-sweep requirement is the whole
safeguard — a single sweep finding two tracks close together is exactly what two drones in close
formation produce, and **a false merge makes one of two real threats vanish from the operator's
screen**, which is far worse than a missed merge. The evidence lives in `track_merge_candidates`,
keyed on the ordered pair so `(a,b)` and `(b,a)` cannot accumulate separately, and is discarded the
moment a pair drifts apart or either track stops being `CONFIRMED` — the requirement is
*consecutive*, so a break resets rather than pauses.

`MERGED` is deliberately distinct from `LOST`: a lost track is an object the system stopped seeing,
a merged one is an object still being tracked under another id. Collapsing the two would tell an
operator that a live threat had gone away.

**The hot state is a cache, and it fails open.** Every detection needs its sector's open tracks
before it can be associated — a database round trip at the pipeline's highest-volume hop, inside an
80 ms budget (§5.8). Redis holds one hash per sector, fields keyed by track id, written through on
every change and evicted when a track goes terminal. **PostgreSQL stays the source of truth**: a
miss, a corrupt entry, or an unreachable Redis all read as a miss and the query falls back to the
database. That is the opposite of the gateway's fail-closed rule (§7.8) and deliberately so — there,
an unreachable Redis means a security control is not being enforced; here it means a query is
slower, and refusing to correlate detections because a cache is down turns a degraded system into an
undetected drone (§5.6). The TTL is the lost timeout, so no stale entry can outlive the window in
which its track could still have been a candidate. Set `correlation.hot-state.enabled=false` to run
with no Redis at all — the no-op store is a supported configuration, not a test double.

**The AI boundary is the only synchronous call this service makes, and it never blocks the tracker.**
`TrackClassifier` is a port; its adapter calls the orchestrator's `Infer` with a gRPC deadline behind
a circuit breaker. It sits inside the detection-processing transaction, so every millisecond is one
the consumer is not reading the next detection — the breaker is what turns a *failing* orchestrator
from a full deadline per detection into one fast rejection. The port returns empty rather than
throwing: §5.6's rule is that a track is never dropped, and an unclassified track is still a track.
Classification happens **last**, after the track is persisted and announced, so an unavailable
orchestrator cannot prevent a track being created. Set `correlation.ai.enabled=false` to run with no
AI boundary at all.

**Both the call and the topic consumer exist, and they differ.** `Infer` returns the single best
answer so a track is enriched immediately; `ai.inference.completed.v1` carries *every* service's
result, including ones that arrived after the deadline or were produced for a track this instance did
not ask about. A classification for an unknown or terminal track is discarded — inference is
asynchronous, and reviving a lost or merged track because a late result arrived would be a real
defect.

`track.classified.v1` carries `model_version` and `device`, so a mocked result stays distinguishable
(§6.4). It is **not** broadcast on the gRPC stream: `TrackEventType` is the lifecycle vocabulary and
a classification changes no status.

**Correlation is idempotent.** A detection is associated to a track at most once (a unique
`(track_id, detection_id)` constraint, and a `detectionAlreadyAssociated` guard before processing),
so at-least-once redelivery from Kafka is safe. Persist happens before publish inside the
transaction, so a publish failure rolls the persist back and the detection is reprocessed rather
than leaving a track with no event.

## Read API

`GET /tracks`, `GET /tracks/{id}`, `GET /tracks/{id}/history` — all guarded by `track:read`
(section 7.4), the permission every role holds. There are no mutating endpoints: a track changes
only by consuming detections or by the sweeps, never over HTTP.

`TrackResponse` is deliberately a summary: it carries the estimate but not the predicted trajectory
or CPA. Those are delivered on `track.updated.v1` and the gRPC stream, which is what the operator
console actually reads (§2.5.2, §3.5); putting them on the list endpoint would compute N outlooks per
request for data nothing polls.

## gRPC — `TrackService.SubscribeTracks`

A server stream of the same `Track` messages the topics carry, tagged with the lifecycle event that
produced each (`CREATED`/`UPDATED`/`LOST`/`MERGED`), optionally filtered to a set of sectors. Port
9084. The gateway's WebSocket fan-out (§3.5) is its first consumer.

Fed **in process**, not from Kafka: an instance owns its sectors exclusively, so every change to a
track it owns happens inside it, and a broker round trip would spend latency budget (§5.8) for
nothing. `BroadcastingTrackEventPublisher` sends to Kafka first and then to subscribers — **Kafka's
failure propagates and rolls back the transaction, the broadcast's does not.** The topics are the
durable record; the stream is a live feed with no replay, so failing correlation over one slow
consumer would let a subscriber stop the tracker.

For the same reason a subscriber whose transport is not ready has its event **dropped, not
buffered** — unbounded queueing would let one stalled consumer grow heap inside the service that must
not stop tracking drones (§5.6).

**Authorisation is transport-level (§7.2):** `Service ↔ Service` is mTLS with SPIFFE-style workload
identity, which is what the existing gRPC surfaces in user-service and sensor-ingestion rely on and
why none of them carries `@PreAuthorize`. `track:read` (§7.4) is a *user* permission — the gateway
holds the user's JWT, enforces it, and fans out over its WebSocket. Accepting a user token on this
internal stream would create a second, weaker path to data the gateway already guards.

## Configuration

| Property | Default | Purpose |
|---|---|---|
| `correlation.association.distance-gate-m` | `500` | Max separation between a detection and a track's *predicted* position |
| `correlation.confirmation.threshold` | `3` | Associated detections at which a track confirms |
| `correlation.sector.cell-degrees` | `1.0` | Side of each square sector cell, in degrees |
| `correlation.kalman.process-noise` | `1.0` | White-noise-jerk PSD (q); higher tracks manoeuvre faster |
| `correlation.kalman.measurement-noise` | `25.0` | Position measurement variance (r), m² — 25 ≈ a 5 m std error |
| `correlation.kalman.initial-position-variance` | `100.0` | Initial position uncertainty, m² |
| `correlation.kalman.initial-velocity-variance` | `100.0` | Initial velocity uncertainty, (m/s)² |
| `correlation.kalman.initial-acceleration-variance` | `10.0` | Initial acceleration uncertainty, (m/s²)² |
| `correlation.lifecycle.coast-timeout` | `PT10S` | How long a confirmed track may miss detections before it coasts |
| `correlation.lifecycle.lost-timeout` | `PT30S` | How long any open track may miss detections before it is lost |
| `correlation.lifecycle.check-interval` | `PT5S` | How often the timeout sweep runs |
| `correlation.prediction.horizon` | `PT30S` | How far ahead a track's trajectory is projected |
| `correlation.prediction.step` | `PT5S` | Interval between predicted points |
| `correlation.assets` | `[]` | Protected assets for CPA — `id`, `name`, `latitude`, `longitude`, `altitude-m` |
| `correlation.merge.gate-m` | `150` | How close two estimates must be to be merge candidates |
| `correlation.merge.required-consecutive-sweeps` | `3` | Sweeps in a row a pair must stay inside the gate |
| `correlation.merge.check-interval` | `PT5S` | How often the merge sweep runs — the unit sweeps are counted in |
| `correlation.hot-state.enabled` | `true` | Redis hot in-flight state; `false` reads candidates from PostgreSQL every time |
| `spring.data.redis.*` | `localhost:6379` | Hot-state connection; §7.2 requires TLS + AUTH in production |
| `grpc.server.port` | `9084` | `TrackService.SubscribeTracks` listener |
| `correlation.ai.enabled` | `true` | The synchronous call to `ai-orchestrator`; `false` leaves tracks unclassified |
| `correlation.ai.endpoint` | `localhost:9085` | The orchestrator's `Infer` endpoint |
| `correlation.ai.timeout` | `PT0.5S` | Deadline for one classification call |
