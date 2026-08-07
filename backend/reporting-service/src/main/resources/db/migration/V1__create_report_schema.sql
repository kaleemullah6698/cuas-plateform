-- reporting-service schema (ARCHITECTURE.md §4.1.10).
--
-- Reports are generated asynchronously from an event-projection read model held in this service's own
-- database: reporting consumes the domain topics and projects the facts it needs into report_events,
-- then the generators aggregate over that projection. Database-per-service is absolute (CLAUDE.md §2)
-- — reporting never reads another service's tables. Elasticsearch is the scale-out search mirror for
-- this projection; the ES-backed data source is a documented follow-on behind the same port.

-- The report template catalogue (§4.1.10). A closed set, seeded here; GET /reports/templates reads it.
CREATE TABLE report_templates (
    type        VARCHAR(40) PRIMARY KEY,
    name        VARCHAR(200) NOT NULL,
    description TEXT         NOT NULL,
    formats     VARCHAR(100) NOT NULL   -- comma-separated supported export formats
);

INSERT INTO report_templates (type, name, description, formats) VALUES
    ('INCIDENT',           'Incident Report',
        'One incident, its declaration, timeline of alerts and its closure.', 'PDF,JSON,CSV'),
    ('DAILY_SUMMARY',      'Daily Summary',
        'A day''s tracks, alerts and incidents, counted by outcome.',         'PDF,JSON,CSV'),
    ('SENSOR_PERFORMANCE', 'Sensor Performance',
        'Per-sensor online/degraded/offline transitions over a range.',        'PDF,JSON,CSV'),
    ('FUSION_EFFICACY',    'Fusion Efficacy',
        'True- vs false-positive alert ratio and AI mock-vs-real share.',      'PDF,JSON,CSV');

-- One report generation, from acceptance (202) to completion.
CREATE TABLE report_jobs (
    report_id       UUID PRIMARY KEY,
    type            VARCHAR(40)  NOT NULL,
    format          VARCHAR(10)  NOT NULL,
    parameters_json TEXT         NOT NULL,
    requested_by    VARCHAR(64)  NOT NULL,
    status          VARCHAR(20)  NOT NULL,
    failure_reason  VARCHAR(40),
    detail          TEXT,
    row_count       BIGINT       NOT NULL DEFAULT 0,
    generation_ms   INTEGER,
    correlation_id  VARCHAR(120),
    created_at      TIMESTAMPTZ  NOT NULL,
    started_at      TIMESTAMPTZ,
    completed_at    TIMESTAMPTZ,
    opt_version     BIGINT       NOT NULL DEFAULT 0
);

CREATE INDEX idx_report_jobs_requested_by ON report_jobs (requested_by, created_at DESC);

-- The rendered artifact of a succeeded job, referenced (uri + sha256), never inlined.
CREATE TABLE report_artifacts (
    report_id  UUID PRIMARY KEY REFERENCES report_jobs (report_id) ON DELETE CASCADE,
    uri        VARCHAR(500) NOT NULL,
    sha256     VARCHAR(64)  NOT NULL,
    size_bytes BIGINT       NOT NULL,
    format     VARCHAR(10)  NOT NULL,
    created_at TIMESTAMPTZ  NOT NULL
);

-- The event-projection read model: one row per projected domain event, the source the generators
-- aggregate over. attributes carries the event-type-specific facts as JSON (a disposition, a sensor
-- status, an inference device), kept schemaless so a new fact does not become a migration.
CREATE TABLE report_events (
    id         BIGSERIAL PRIMARY KEY,
    event_type VARCHAR(60)  NOT NULL,
    subject_id UUID,
    occurred_at TIMESTAMPTZ NOT NULL,
    event_day  DATE         NOT NULL,
    attributes TEXT         NOT NULL DEFAULT '{}',
    dedup_key  VARCHAR(200) NOT NULL,
    created_at TIMESTAMPTZ  NOT NULL,
    CONSTRAINT uq_report_event UNIQUE (dedup_key)
);

CREATE INDEX idx_report_events_day  ON report_events (event_day, event_type);
CREATE INDEX idx_report_events_type ON report_events (event_type, occurred_at);
CREATE INDEX idx_report_events_subject ON report_events (subject_id);
