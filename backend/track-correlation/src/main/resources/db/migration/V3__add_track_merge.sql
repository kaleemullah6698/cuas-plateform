-- M5.5: cross-sensor duplicate elimination (ARCHITECTURE.md section 4.1.5).
--
-- Two additions.
--
-- 1. tracks.merged_into_track_id — the survivor a merged track was absorbed into. Null for every
--    other status; a self-reference, since the survivor is another row in this same table. There is
--    no ON DELETE clause because tracks are not deleted.
--
-- 2. track_merge_candidates — the evidence behind the consecutive-sweep requirement. A single sweep
--    finding two tracks inside the merge gate is a coincidence (two drones in close formation
--    produce exactly that), and a false merge makes one of two real threats vanish from the
--    operator's screen. Requiring the pair to stay together across several sweeps is what separates
--    one object seen twice from two objects flying together.
--
--    The pair is stored lower-id-first with a CHECK enforcing it, so (a,b) and (b,a) are necessarily
--    the same row. Without that ordering a pair could accumulate evidence twice under two orderings
--    and merge on half the intended evidence.

ALTER TABLE tracks ADD COLUMN merged_into_track_id UUID REFERENCES tracks (track_id);

CREATE INDEX idx_tracks_merged_into ON tracks (merged_into_track_id)
    WHERE merged_into_track_id IS NOT NULL;

CREATE TABLE track_merge_candidates (
    lower_track_id     UUID        NOT NULL REFERENCES tracks (track_id),
    higher_track_id    UUID        NOT NULL REFERENCES tracks (track_id),
    consecutive_sweeps INTEGER     NOT NULL,
    first_observed_at  TIMESTAMPTZ NOT NULL,
    last_observed_at   TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (lower_track_id, higher_track_id),
    CONSTRAINT ck_merge_candidate_ordered CHECK (lower_track_id < higher_track_id),
    CONSTRAINT ck_merge_candidate_sweeps  CHECK (consecutive_sweeps >= 1)
);

-- A track leaving CONFIRMED must drop every candidacy it is in; both directions are looked up.
CREATE INDEX idx_merge_candidates_higher ON track_merge_candidates (higher_track_id);
