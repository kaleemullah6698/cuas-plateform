-- Alert and incident schema — ARCHITECTURE.md sections 4.1.8, 5.3.4, 2.4.3.
--
--   alerts                  the alert aggregate; one row per alert, mutated through its lifecycle
--   alert_acknowledgements  who saw an alert, and when
--   alert_escalations       every rung climbed, and why
--   alert_resolutions       how each alert ended
--   incidents               operator-declared investigations
--   incident_events         the section 2.4.3 timeline, assembled from platform events
--   incident_notes          operator narrative, append-only
--   incident_evidence       references into the evidence store, never payloads
--
-- Section 4.1.8 names alerts, alert_acknowledgements, alert_escalations, incidents,
-- incident_events, incident_notes and incident_evidence. alert_resolutions is the one addition:
-- section 4.1.8 lists resolution as a responsibility and gives it a disposition, but names no table
-- for it, and folding a disposition and a resolving actor onto the alerts row would make the
-- lifecycle history asymmetric — acknowledgements and escalations recorded properly, resolutions
-- only as columns. The asymmetry would show up the first time anybody asked how long alerts stayed
-- open by disposition.
--
-- The one constraint everything else serves is uq_alert_open_dedup. Section 5.7 names duplicate
-- alerts as the failure that destroys operator trust faster than any other, and application-level
-- deduplication alone cannot survive two instances processing the same threat transition after a
-- consumer group rebalance. The partial unique index below is what actually makes it impossible.

CREATE TABLE alerts (
    alert_id        UUID        PRIMARY KEY,       -- UUIDv7, time-sortable
    reference       TEXT        NOT NULL,          -- operator-facing handle, e.g. ALT-9912
    dedup_key       TEXT        NOT NULL,          -- track | severity | policy version
    status          TEXT        NOT NULL,

    track_id        UUID        NOT NULL,
    sector_id       TEXT        NOT NULL DEFAULT '',
    assessment_id   UUID,
    severity        TEXT        NOT NULL,
    recommendation  TEXT        NOT NULL,
    threat_score    DOUBLE PRECISION NOT NULL,

    title           TEXT        NOT NULL DEFAULT '',
    summary         TEXT        NOT NULL DEFAULT '',

    raised_at       TIMESTAMPTZ NOT NULL,
    acknowledged_at TIMESTAMPTZ,
    resolved_at     TIMESTAMPTZ,

    escalation_level INTEGER    NOT NULL DEFAULT 0,
    -- The authoritative escalation deadline. The Redis sorted set is an index over this column,
    -- not a replacement for it: a deadline that lived only in a cache would mean an unacknowledged
    -- critical alert silently ceasing to escalate the moment that cache restarted.
    escalates_at    TIMESTAMPTZ,
    occurrence_count INTEGER    NOT NULL DEFAULT 1,

    incident_id     UUID,
    assigned_to     TEXT,
    correlation_id  TEXT        NOT NULL DEFAULT '',
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    version         BIGINT      NOT NULL DEFAULT 0,  -- optimistic locking

    CONSTRAINT ck_alert_status CHECK (status IN (
        'RAISED', 'ACKNOWLEDGED', 'ESCALATED', 'RESOLVED', 'SUPPRESSED')),
    CONSTRAINT ck_alert_severity CHECK (severity IN ('LOW', 'MEDIUM', 'HIGH', 'CRITICAL')),
    -- The closed section 6.3.4 advisory vocabulary, enforced at the database boundary as well as in
    -- the enum. An alert relays what was advised, and this is what stops a hand-written INSERT
    -- putting an instruction on an operator's screen that the platform's scope limit forbids.
    CONSTRAINT ck_alert_recommendation CHECK (recommendation IN (
        'MONITOR', 'TRACK_AND_MONITOR', 'REQUEST_VISUAL_ID',
        'ESCALATE_TO_SUPERVISOR', 'RECOMMEND_OPERATOR_REVIEW')),
    CONSTRAINT ck_alert_occurrences CHECK (occurrence_count >= 1),
    -- A resolved alert has a resolution time; an open one does not.
    CONSTRAINT ck_alert_resolved_at CHECK (
        (status = 'RESOLVED') = (resolved_at IS NOT NULL)),
    -- A terminal alert never has a live escalation deadline. Without this, resolving an alert while
    -- leaving escalates_at set would let the database sweep resurrect its escalation.
    CONSTRAINT ck_alert_terminal_has_no_deadline CHECK (
        status NOT IN ('RESOLVED', 'SUPPRESSED') OR escalates_at IS NULL)
);

COMMENT ON TABLE alerts IS
    'The alert aggregate. uq_alert_open_dedup is what makes duplicate alerts impossible rather '
    'than merely unlikely (section 5.7).';

-- One open alert per deduplication identity. Partial, so a resolved alert does not block the same
-- condition recurring later: a track that was CRITICAL, was dealt with, and becomes CRITICAL again
-- an hour later is genuinely a new alert.
CREATE UNIQUE INDEX uq_alert_open_dedup ON alerts (dedup_key)
    WHERE status NOT IN ('RESOLVED', 'SUPPRESSED');

CREATE INDEX idx_alerts_status_raised ON alerts (status, raised_at DESC);
CREATE INDEX idx_alerts_severity_raised ON alerts (severity, raised_at DESC);
CREATE INDEX idx_alerts_sector_raised ON alerts (sector_id, raised_at DESC);
CREATE INDEX idx_alerts_track ON alerts (track_id);
CREATE INDEX idx_alerts_incident ON alerts (incident_id);
-- The database half of the escalation sweep: open alerts whose deadline has passed. Partial, so the
-- index holds only alerts that can still escalate.
CREATE INDEX idx_alerts_escalations_due ON alerts (escalates_at)
    WHERE escalates_at IS NOT NULL AND status IN ('RAISED', 'ESCALATED');

CREATE TABLE alert_acknowledgements (
    id          BIGSERIAL   PRIMARY KEY,
    alert_id    UUID        NOT NULL REFERENCES alerts (alert_id),
    actor_id    TEXT        NOT NULL,
    actor_name  TEXT        NOT NULL DEFAULT '',
    note        TEXT        NOT NULL DEFAULT '',
    occurred_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_alert_ack_alert ON alert_acknowledgements (alert_id, occurred_at);

CREATE TABLE alert_escalations (
    id             BIGSERIAL   PRIMARY KEY,
    alert_id       UUID        NOT NULL REFERENCES alerts (alert_id),
    previous_level INTEGER     NOT NULL,
    current_level  INTEGER     NOT NULL,
    trigger        TEXT        NOT NULL,
    actor_id       TEXT,                            -- null when the scheduler fired it
    occurred_at    TIMESTAMPTZ NOT NULL,
    next_escalates_at TIMESTAMPTZ,                  -- null at the terminal rung

    CONSTRAINT ck_escalation_trigger CHECK (trigger IN (
        'UNACKNOWLEDGED', 'LEVEL_INCREASE', 'MANUAL')),
    CONSTRAINT ck_escalation_ascends CHECK (current_level > previous_level)
);

CREATE INDEX idx_alert_escalations_alert ON alert_escalations (alert_id, occurred_at);

CREATE TABLE alert_resolutions (
    id            BIGSERIAL   PRIMARY KEY,
    alert_id      UUID        NOT NULL REFERENCES alerts (alert_id),
    disposition   TEXT        NOT NULL,
    note          TEXT        NOT NULL DEFAULT '',
    actor_id      TEXT,                             -- null only for an auto-resolution
    actor_name    TEXT        NOT NULL DEFAULT '',
    -- An alert closed because its track was lost is not evidence anybody looked at it. Pooling the
    -- two would make the false-positive rate reporting computes from dispositions wrong.
    auto_resolved BOOLEAN     NOT NULL DEFAULT FALSE,
    occurred_at   TIMESTAMPTZ NOT NULL,

    CONSTRAINT ck_resolution_disposition CHECK (disposition IN (
        'TRUE_POSITIVE', 'FALSE_POSITIVE', 'BENIGN', 'DUPLICATE', 'INCONCLUSIVE')),
    CONSTRAINT ck_resolution_actor CHECK (auto_resolved OR actor_id IS NOT NULL)
);

CREATE INDEX idx_alert_resolutions_alert ON alert_resolutions (alert_id);
CREATE INDEX idx_alert_resolutions_disposition ON alert_resolutions (disposition, occurred_at DESC);

-- ── Incidents ───────────────────────────────────────────────────────────────────────────────────

CREATE TABLE incidents (
    incident_id      UUID        PRIMARY KEY,
    reference        TEXT        NOT NULL UNIQUE,   -- INC-2026-0087; quoted in reports
    status           TEXT        NOT NULL,
    severity         TEXT        NOT NULL,
    title            TEXT        NOT NULL DEFAULT '',
    sector_id        TEXT        NOT NULL DEFAULT '',

    -- A user id, never a service. Section 2.4.3 records incident declaration as a human act, and a
    -- system that could open one by itself would make that record untrue.
    declared_by      TEXT        NOT NULL,
    declared_by_name TEXT        NOT NULL DEFAULT '',
    assigned_to      TEXT,
    assigned_to_name TEXT        NOT NULL DEFAULT '',

    opened_at        TIMESTAMPTZ NOT NULL,
    closed_at        TIMESTAMPTZ,
    closure_disposition TEXT,
    closure_summary  TEXT        NOT NULL DEFAULT '',

    -- Section 2.4.3's decision chain.
    decision_system_recommended TEXT,
    decision_operator           TEXT NOT NULL DEFAULT '',
    decision_authorised_by      TEXT,
    decision_authorised_by_name TEXT NOT NULL DEFAULT '',
    decision_authorised_at      TIMESTAMPTZ,
    decision_audit_ref          TEXT NOT NULL DEFAULT '',
    -- Exactly one legal value, permanently. The platform emits advisory recommendations only; RF
    -- jamming, GNSS spoofing and cyber takeover are outside the software boundary. A CHECK with one
    -- allowed value is how an exported evidence bundle asserts that structurally rather than by
    -- omission. Adding a value is an architecture amendment, not a migration written in passing.
    decision_engagement_action  TEXT NOT NULL DEFAULT 'NONE',

    correlation_id   TEXT        NOT NULL DEFAULT '',
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    version          BIGINT      NOT NULL DEFAULT 0,

    CONSTRAINT ck_incident_status CHECK (status IN ('OPEN', 'UNDER_REVIEW', 'CLOSED')),
    CONSTRAINT ck_incident_severity CHECK (severity IN ('LOW', 'MEDIUM', 'HIGH', 'CRITICAL')),
    CONSTRAINT ck_incident_closure CHECK (
        (status = 'CLOSED') = (closed_at IS NOT NULL)),
    CONSTRAINT ck_incident_closure_disposition CHECK (
        closure_disposition IS NULL OR closure_disposition IN (
            'TRUE_POSITIVE', 'FALSE_POSITIVE', 'BENIGN', 'DUPLICATE', 'INCONCLUSIVE')),
    CONSTRAINT ck_incident_recommendation CHECK (
        decision_system_recommended IS NULL OR decision_system_recommended IN (
            'MONITOR', 'TRACK_AND_MONITOR', 'REQUEST_VISUAL_ID',
            'ESCALATE_TO_SUPERVISOR', 'RECOMMEND_OPERATOR_REVIEW')),
    CONSTRAINT ck_incident_engagement_action CHECK (decision_engagement_action = 'NONE'),
    CONSTRAINT ck_incident_declared_by CHECK (length(trim(declared_by)) > 0)
);

CREATE INDEX idx_incidents_status_opened ON incidents (status, opened_at DESC);
CREATE INDEX idx_incidents_opened ON incidents (opened_at DESC);

-- An incident spans tracks and alerts, and the sets only grow. A drone that splits into two tracks
-- is one investigation, and removing a track after the fact would erase part of what was examined.
CREATE TABLE incident_tracks (
    incident_id UUID NOT NULL REFERENCES incidents (incident_id) ON DELETE CASCADE,
    track_id    UUID NOT NULL,
    PRIMARY KEY (incident_id, track_id)
);

CREATE TABLE incident_alerts (
    incident_id UUID NOT NULL REFERENCES incidents (incident_id) ON DELETE CASCADE,
    alert_id    UUID NOT NULL REFERENCES alerts (alert_id),
    PRIMARY KEY (incident_id, alert_id)
);

CREATE TABLE incident_events (
    event_id     UUID        PRIMARY KEY,
    incident_id  UUID        NOT NULL REFERENCES incidents (incident_id) ON DELETE CASCADE,
    source_topic TEXT        NOT NULL DEFAULT '',
    source_id    TEXT        NOT NULL DEFAULT '',
    summary      TEXT        NOT NULL,
    -- Empty means a system entry, which is what section 2.4.3 renders as SYS rather than USER. The
    -- distinction is load-bearing: an automated step must not read as a human decision.
    actor_id     TEXT,
    actor_name   TEXT        NOT NULL DEFAULT '',
    occurred_at  TIMESTAMPTZ NOT NULL,

    CONSTRAINT ck_event_summary CHECK (length(trim(summary)) > 0)
);

CREATE INDEX idx_incident_events_incident ON incident_events (incident_id, occurred_at);

CREATE TABLE incident_notes (
    note_id     UUID        PRIMARY KEY,
    incident_id UUID        NOT NULL REFERENCES incidents (incident_id) ON DELETE CASCADE,
    author_id   TEXT        NOT NULL,
    author_name TEXT        NOT NULL DEFAULT '',
    body        TEXT        NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL,

    CONSTRAINT ck_note_author CHECK (length(trim(author_id)) > 0),
    CONSTRAINT ck_note_body CHECK (length(trim(body)) > 0)
);

CREATE INDEX idx_incident_notes_incident ON incident_notes (incident_id, created_at);

CREATE TABLE incident_evidence (
    evidence_id  UUID        PRIMARY KEY,
    incident_id  UUID        NOT NULL REFERENCES incidents (incident_id) ON DELETE CASCADE,
    kind         TEXT        NOT NULL,
    -- A reference, never a payload. An IQ capture or a video frame in this column would put
    -- megabytes into a database sized for metadata, and onto every backup of it.
    uri          TEXT        NOT NULL,
    -- What makes an exported bundle verifiable after it leaves the platform's custody.
    sha256       TEXT        NOT NULL,
    size_bytes   BIGINT      NOT NULL DEFAULT 0,
    content_type TEXT        NOT NULL DEFAULT 'application/octet-stream',
    source_id    TEXT        NOT NULL DEFAULT '',
    captured_at  TIMESTAMPTZ NOT NULL,

    CONSTRAINT ck_evidence_kind CHECK (kind IN (
        'DETECTION', 'TRACK_SNAPSHOT', 'INFERENCE', 'ASSESSMENT',
        'FRAME', 'SPECTROGRAM', 'AUDIT_EXTRACT', 'OPERATOR_NOTE')),
    CONSTRAINT ck_evidence_sha256 CHECK (sha256 ~ '^[0-9a-f]{64}$'),
    -- The same object attached twice is one piece of evidence.
    CONSTRAINT uq_evidence_per_incident UNIQUE (incident_id, sha256)
);

CREATE INDEX idx_incident_evidence_incident ON incident_evidence (incident_id);
