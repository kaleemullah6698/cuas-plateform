-- Sensor ingestion schema — ARCHITECTURE.md section 4.1.4.
--
-- Three tables, one per concern the service owns locally:
--   sensors          the set of sensors the service has seen, learned from traffic
--   sensor_health    the latest liveness and clock-drift state per sensor
--   ingestion_stats  per-sensor counters, the operational feed behind health telemetry
--
-- The deduplication window is not here: it is Redis-backed (section 4.1.4), because it is a
-- short-lived, high-churn set keyed by (sensorId, sensorSeq), not durable state.

-- A sensor is registered the first time a detection from it is accepted. Authoritative
-- provisioning via platform.config.sensor.updated.v1 lands in a later sub-milestone; until then
-- the registry is learned from what actually reports, which is enough to attribute health and
-- stats without inventing configuration the service has not been told.
CREATE TABLE sensors (
    sensor_id     TEXT        PRIMARY KEY,
    modality      TEXT        NOT NULL,          -- last observed modality (SensorModality name)
    first_seen_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    last_seen_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

COMMENT ON TABLE sensors IS 'Sensors known to this service, learned on first accepted detection.';

-- Liveness and the measured clock correction, one row per sensor. Updated on every accepted
-- detection and read by the scheduled health emitter that publishes sensor.health.v1.
CREATE TABLE sensor_health (
    sensor_id         TEXT        PRIMARY KEY REFERENCES sensors (sensor_id) ON DELETE CASCADE,
    status            TEXT        NOT NULL DEFAULT 'ONLINE',   -- ONLINE | DEGRADED | OFFLINE
    last_detection_at TIMESTAMPTZ,                             -- platform clock at last accepted detection
    clock_drift_ms    BIGINT      NOT NULL DEFAULT 0,          -- current EWMA drift estimate (section 5.3)
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Per-sensor counters. Separated from sensor_health so a high-frequency counter update does not
-- churn the row the emitter reads, and so the operational feed is queryable on its own.
CREATE TABLE ingestion_stats (
    sensor_id        TEXT        PRIMARY KEY REFERENCES sensors (sensor_id) ON DELETE CASCADE,
    received_total   BIGINT      NOT NULL DEFAULT 0,   -- raw records consumed
    normalized_total BIGINT      NOT NULL DEFAULT 0,   -- normalized records published
    duplicate_total  BIGINT      NOT NULL DEFAULT 0,   -- dropped by the dedup window
    invalid_total    BIGINT      NOT NULL DEFAULT 0,   -- rejected by validation
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT now()
);
