# sensor-ingestion

Heterogeneous sensor input, normalised to the canonical `DetectionRecord` on Kafka.

- **Port:** 8083 (HTTP), 9083 (gRPC)
- **Database:** `sensordb` (`sensors`, `sensor_health`, `ingestion_stats`); Redis for the dedup window and drift state
- **Specification:** `docs/ARCHITECTURE.md` sections 4.1.4, 5.3, 5.6

## The pipeline

```
sensor/adapter ─▶ POST /ingest/detections      ┐
                  gRPC StreamDetections         ├─▶ sensor.detection.raw.v1
                                                ┘         │
                                    (consume) ◀───────────┘
                          validate ─ dedup ─ clock-correct ─ assign UUIDv7 + ingest_time
                                                │
                                                ▼
                                    sensor.detection.normalized.v1 ─▶ track-correlation
```

Intake and normalisation are decoupled through the raw topic (section 5.6): the intake adapters
publish a detection verbatim to `sensor.detection.raw.v1`, and a separate consumer reads it back,
normalises it, and publishes `sensor.detection.normalized.v1`. That is what lets a burst be absorbed
by Kafka instead of blocking a sensor.

## Three things worth knowing before changing this service

**The intake carries no RBAC.** Sensors are not users (the M4 decision): the intake endpoints have
no `@PreAuthorize` and no JWT. Sensor authentication is an infrastructure concern — mTLS or a
trusted network — not an application permission. If an *operator*-facing read of sensor state is
ever added, that endpoint would carry `sensor:read` from the section 7.4 matrix; the intake never
does.

**A detection is marked de-duplicated only after it is published.** The dedup window
(`seenBefore` / `markSeen`, Redis) is checked before publishing but marked after, so a publish
failure leaves the record unmarked and the redelivery is normalised again rather than dropped. The
governing rule is section 5.6's: a detection is never lost. The cost is that a crash in the narrow
window between publishing and marking can republish once; track-correlation's cross-sensor
de-duplication (section 4.1.5) absorbs that. This is at-least-once, deliberately.

**The clock is corrected, never overwritten.** `sensor_time` is preserved exactly as the sensor
reported it (section 5.3). The drift is a separate, smoothed measurement — an EWMA of
`ingest_time - sensor_time` per sensor — recorded in `clock_drift_ms`, and `ingest_time` is the
authoritative platform clock. Time synchronisation across heterogeneous sensors is the failure mode
that silently destroys fusion quality, so it is made explicit and auditable.

## REST intake

`POST /ingest/detections` accepts a batch of canonical `DetectionRecord` objects as protobuf JSON:

```json
{ "detections": [ { "sensorId": "rf-01", "modality": "MODALITY_RF",
                    "sensorTime": "2026-08-04T12:00:00Z", "sensorSeq": "1", "quality": 0.9,
                    "measurements": { "rf": { "centerFrequencyHz": 2450000000 } } } ] }
```

The response reports how many were accepted and the per-detection reason for any that were not, so
one malformed record never rejects a co-batched detection from another sensor. `202` on processing,
`400` empty batch, `413` over the configured batch limit, `503` when the broker is unavailable.

The canonical schema is accepted directly rather than through a hand-written DTO tree: it is the one
wire contract (section 5.3), and a parallel DTO hierarchy for the same eight nested messages would
be a second, drifting source of truth for it.

## Configuration

| Property | Default | Purpose |
|---|---|---|
| `ingestion.dedup.ttl` | `PT10M` | How long a `(sensorId, sensorSeq)` pair is remembered as seen |
| `ingestion.drift.alpha` | `0.2` | EWMA smoothing factor; higher tracks a drifting clock faster |
| `ingestion.drift.ttl` | `PT24H` | How long a sensor's drift estimate is retained without a detection |
| `ingestion.health.online-within` / `.degraded-within` | `PT30S` / `PT2M` | Silence thresholds behind `ONLINE`/`DEGRADED`/`OFFLINE` |
| `ingestion.health.emit-interval` | `PT15S` | How often health is recomputed and published |
| `ingestion.batch.max-size` | `500` | Largest REST batch accepted in one request |

## Dataset replay (development only)

`POST /ingest/replay` reads newline-delimited `DetectionRecord` JSON and feeds each detection through
the **same** intake as a live request — no shortcut into Kafka or the database — so a recorded
dataset exercises the whole pipeline. It is registered only when `ingestion.replay.enabled` is true
(off by default); a disabled instance returns 404, because replay can push arbitrary detections and
must never be reachable in a sensor-facing deployment. The companion `sensor-simulator` module drives
this and the live intake over real REST and gRPC.

## Not in this service

The `platform.config.sensor.updated.v1` consumer (an authoritative sensor registry; the service
currently learns sensors on first detection), the `sensor.ingestion.error.v1` publisher (needs a
contract message), the section 7.8 per-sensor rate limit at the gateway, DLQ routing, and the
section 5.6 lowest-quality-first drop under producer-buffer pressure are operational-hardening
follow-ups. None affect the normalised output that downstream services consume. See
`docs/ROADMAP.md`.
