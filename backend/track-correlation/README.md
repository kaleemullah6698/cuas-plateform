# track-correlation

Normalised detections associated into tracks.

- **Port:** 8084
- **Database:** `trackdb` (`tracks`, `track_states`, `track_detections`, `track_lifecycle_events`)
- **Specification:** `docs/ARCHITECTURE.md` sections 4.1.5, 5.3.2

## The pipeline

```
sensor.detection.normalized.v1 ─▶ (consume)
     place in sector ─ predict each candidate to the detection's time ─ gate ─ nearest-neighbour
          match ─▶ Kalman predict-then-update, extend track ─▶ track.updated.v1
          no match ─▶ open a new tentative track            ─▶ track.created.v1

timeout sweep (every check-interval)
     confirmed, unfed past coast-timeout ─▶ predict forward, COASTING ─▶ track.updated.v1
     open,      unfed past lost-timeout  ─▶ LOST (terminal)           ─▶ track.lost.v1
```

A detection is placed in a geographic sector and compared against the open tracks recently seen
there. Each candidate is first *predicted* by its own Kalman filter to the detection's time, and the
detection extends the nearest track whose predicted position falls inside the distance gate;
matching none, it opens a new tentative track. A track confirms once enough detections have
associated to it. The aggregate and its full history — every state, association, and lifecycle
transition — are persisted, and the track is published on `track.created.v1` / `track.updated.v1` /
`track.lost.v1`.

## Four things worth knowing before changing this service

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
detection changed concurrently simply loses the race and is retried on the next tick. Concurrent
per-sector processing and the Redis hot-state cache (section 4.1.5) are the scaling half of the
sector model, still deferred. The sector is assigned and association is scoped to it; the exclusive
per-instance sector ownership is not yet built.

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

**Still to come in M5.** Cross-sensor duplicate elimination (`track.merged.v1`), the Redis hot-state
cache, and the gRPC `SubscribeTracks` stream. The circuit-broken gRPC call to `ai-orchestrator` and
the `ai.inference.completed.v1` consumer behind `track.classified.v1` are deferred to M6, where that
service first exists.

**Correlation is idempotent.** A detection is associated to a track at most once (a unique
`(track_id, detection_id)` constraint, and a `detectionAlreadyAssociated` guard before processing),
so at-least-once redelivery from Kafka is safe. Persist happens before publish inside the
transaction, so a publish failure rolls the persist back and the detection is reprocessed rather
than leaving a track with no event.

## Read API

`GET /tracks`, `GET /tracks/{id}`, `GET /tracks/{id}/history` — all guarded by `track:read`
(section 7.4), the permission every role holds. There are no mutating endpoints: a track changes
only by consuming detections or by the timeout sweep, never over HTTP. The gRPC `SubscribeTracks`
stream (section 4.1.5) is not yet built.

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
