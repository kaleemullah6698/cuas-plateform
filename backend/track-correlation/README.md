# track-correlation

Normalised detections associated into tracks.

- **Port:** 8084
- **Database:** `trackdb` (`tracks`, `track_states`, `track_detections`, `track_lifecycle_events`)
- **Specification:** `docs/ARCHITECTURE.md` sections 4.1.5, 5.3.2

## The pipeline (M5.1 foundation)

```
sensor.detection.normalized.v1 ─▶ (consume)
     place in sector ─ gate on space & time ─ nearest-neighbour associate
          match ─▶ extend track (may confirm)      ─▶ track.updated.v1
          no match ─▶ open a new tentative track    ─▶ track.created.v1
```

A detection is placed in a geographic sector, compared against the open tracks recently seen there,
and either extends the nearest one within the distance and time gates or, matching none, opens a new
tentative track. A track confirms once enough detections have associated to it. The aggregate and
its full history — every state, association, and lifecycle transition — are persisted, and the track
is published on `track.created.v1` / `track.updated.v1`.

## Three things worth knowing before changing this service

**This is the foundation, not the finished correlator.** Association is a static space-time gate with
nearest-neighbour selection; there is no motion model. Kalman-filtered state estimation, trajectory
prediction, CPA, cross-sensor fusion, and the coasting/lost transitions are later M5 sub-milestones.
The `Track` state carried today is simply the latest associated detection's position and velocity.

**It runs single-threaded, on purpose.** The consumer's concurrency is one, which is what lets the
read-modify-write on a track be a plain transaction. Concurrent per-sector processing and the Redis
hot-state cache (section 4.1.5) are the scaling half of the sector model — deferred to M5.2. The
sector is assigned and association is scoped to it; the exclusive per-instance sector ownership is
not yet built.

**Correlation is idempotent.** A detection is associated to a track at most once (a unique
`(track_id, detection_id)` constraint, and a `detectionAlreadyAssociated` guard before processing),
so at-least-once redelivery from Kafka is safe. Persist happens before publish inside the
transaction, so a publish failure rolls the persist back and the detection is reprocessed rather
than leaving a track with no event.

## Read API

`GET /tracks`, `GET /tracks/{id}`, `GET /tracks/{id}/history` — all guarded by `track:read`
(section 7.4), the permission every role holds. There are no mutating endpoints: a track changes
only by consuming detections, never over HTTP. The gRPC `SubscribeTracks` stream (section 4.1.5) is
deferred to the real-time edge work (M9).

## Configuration

| Property | Default | Purpose |
|---|---|---|
| `correlation.association.distance-gate-m` | `500` | Max separation for a detection to extend a track |
| `correlation.association.time-gate` | `PT30S` | Max time gap between a track's last state and a detection |
| `correlation.confirmation.threshold` | `3` | Associated detections at which a track confirms |
| `correlation.sector.cell-degrees` | `1.0` | Side of each square sector cell, in degrees |
