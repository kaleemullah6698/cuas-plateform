-- M5.2: persist the complete estimated track state (ARCHITECTURE.md section 4.1.5).
--
-- Two additions to the tracks row:
--   observed_at   the time the current estimate is valid for. Equal to last_seen while a track is
--                 being fed detections; a coasting prediction advances it past last_seen (which
--                 stays at the last detection, since that is what the miss timeouts count from).
--   filter_state  the serialised Kalman filter state (origin + per-axis mean and covariance), so
--                 the filter continues correctly across detections and restarts rather than
--                 starting cold.
--
-- The temporary defaults populate any rows that already exist (a development-only M5.1 track had no
-- Kalman state, so filter_state defaults to empty and observed_at is backfilled from last_seen);
-- the defaults are then dropped so every new track must supply both.

ALTER TABLE tracks ADD COLUMN observed_at  TIMESTAMPTZ NOT NULL DEFAULT now();
ALTER TABLE tracks ADD COLUMN filter_state TEXT        NOT NULL DEFAULT '';

UPDATE tracks SET observed_at = last_seen;

ALTER TABLE tracks ALTER COLUMN observed_at  DROP DEFAULT;
ALTER TABLE tracks ALTER COLUMN filter_state DROP DEFAULT;
