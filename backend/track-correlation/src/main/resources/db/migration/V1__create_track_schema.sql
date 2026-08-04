-- Track correlation schema — ARCHITECTURE.md section 4.1.5.
--
--   tracks                  the current state of each track (the aggregate root)
--   track_states            the history of observed states, one row per associated detection
--   track_detections        which detections were associated to which track, and how close
--   track_lifecycle_events  the record of every status transition
--
-- The history tables are append-only in spirit; they are the audit of how a track came to be.
-- Section 4.1.5 calls for time-partitioning track_states and a Redis hot-state cache; both are
-- scaling optimisations and are deferred (M5.2+). A plain table with the right indexes is correct
-- and sufficient for the foundation.

CREATE TABLE tracks (
    track_id          UUID        PRIMARY KEY,
    sector_id         TEXT        NOT NULL,
    status            TEXT        NOT NULL,          -- TENTATIVE | CONFIRMED (COASTING/LOST in M5.2)
    latitude          DOUBLE PRECISION NOT NULL,
    longitude         DOUBLE PRECISION NOT NULL,
    altitude_m        DOUBLE PRECISION NOT NULL,
    speed_ms          DOUBLE PRECISION NOT NULL,
    heading_deg       DOUBLE PRECISION NOT NULL,
    last_detection_id TEXT        NOT NULL,
    last_sensor_id    TEXT        NOT NULL,        -- sensor that reported the current state
    detection_count   INTEGER     NOT NULL,
    first_seen        TIMESTAMPTZ NOT NULL,          -- observed (ingest) time of the first detection
    last_seen         TIMESTAMPTZ NOT NULL,          -- observed (ingest) time of the latest detection
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    version           BIGINT      NOT NULL DEFAULT 0 -- optimistic locking
);

COMMENT ON TABLE tracks IS 'Current state of each track; history lives in track_states.';

-- Candidate lookup for association is "open tracks in this sector seen recently", so the sector
-- and recency are indexed together.
CREATE INDEX idx_tracks_sector_last_seen ON tracks (sector_id, last_seen DESC);
CREATE INDEX idx_tracks_status ON tracks (status);

CREATE TABLE track_states (
    id           BIGSERIAL   PRIMARY KEY,
    track_id     UUID        NOT NULL REFERENCES tracks (track_id) ON DELETE CASCADE,
    latitude     DOUBLE PRECISION NOT NULL,
    longitude    DOUBLE PRECISION NOT NULL,
    altitude_m   DOUBLE PRECISION NOT NULL,
    speed_ms     DOUBLE PRECISION NOT NULL,
    heading_deg  DOUBLE PRECISION NOT NULL,
    observed_at  TIMESTAMPTZ NOT NULL,               -- ingest time of the detection this state came from
    detection_id TEXT        NOT NULL,
    sensor_id    TEXT        NOT NULL,
    recorded_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_track_states_track_observed ON track_states (track_id, observed_at DESC);

CREATE TABLE track_detections (
    id            BIGSERIAL   PRIMARY KEY,
    track_id      UUID        NOT NULL REFERENCES tracks (track_id) ON DELETE CASCADE,
    detection_id  TEXT        NOT NULL,
    sensor_id     TEXT        NOT NULL,
    sensor_seq    BIGINT      NOT NULL,
    distance_m    DOUBLE PRECISION,                  -- gate distance at association; null when the track was created
    associated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    -- A detection is associated to a track at most once; at-least-once redelivery is idempotent.
    CONSTRAINT uq_track_detection UNIQUE (track_id, detection_id)
);

CREATE INDEX idx_track_detections_track ON track_detections (track_id);

CREATE TABLE track_lifecycle_events (
    id          BIGSERIAL   PRIMARY KEY,
    -- No ON DELETE CASCADE: the immutability rule below would turn a cascade into a no-op and
    -- orphan rows. Tracks are not deleted in the foundation; history outlives the row it describes.
    track_id    UUID        NOT NULL REFERENCES tracks (track_id),
    from_status TEXT,                                -- null on creation
    to_status   TEXT        NOT NULL,
    reason      TEXT        NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_track_lifecycle_track ON track_lifecycle_events (track_id, occurred_at);

-- A lifecycle event is history: it is never rewritten.
CREATE RULE track_lifecycle_no_update AS ON UPDATE TO track_lifecycle_events DO INSTEAD NOTHING;
CREATE RULE track_lifecycle_no_delete AS ON DELETE TO track_lifecycle_events DO INSTEAD NOTHING;
