-- Notification delivery schema — ARCHITECTURE.md section 4.1.9.
--
--   user_preferences     per-user quiet hours and the minimum severity worth a notification
--   notification_channels per-user delivery addresses, one row per channel, with a preference order
--   notification_log     one row per notification (a recipient notified about one thing), its
--                        current delivery status, and the retry schedule
--   delivery_receipts    one row per delivery attempt — the receipt trail behind a log row
--
-- Cross-channel deduplication is a database invariant, not a convention: uq_notification_dedup makes
-- it impossible to notify one recipient twice for the same thing, however many channels they enabled
-- or however many times the source event recurs (section 4.1.9).

CREATE TABLE user_preferences (
    user_id           UUID        PRIMARY KEY,
    quiet_hours_start TIME,                              -- null = no quiet hours
    quiet_hours_end   TIME,
    min_severity      TEXT        NOT NULL DEFAULT 'LOW',  -- LOW | MEDIUM | HIGH | CRITICAL
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE notification_channels (
    id         BIGSERIAL   PRIMARY KEY,
    user_id    UUID        NOT NULL,
    channel    TEXT        NOT NULL,                     -- WEBSOCKET | EMAIL | SMS | WEBHOOK
    address    TEXT        NOT NULL,                     -- email, phone, webhook URL; user id for websocket
    enabled    BOOLEAN     NOT NULL DEFAULT TRUE,
    priority   INTEGER     NOT NULL DEFAULT 100,         -- lower is preferred; the cross-channel choice
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_channel_per_user UNIQUE (user_id, channel)
);

CREATE INDEX idx_channels_user ON notification_channels (user_id);

CREATE TABLE notification_log (
    notification_id     UUID        PRIMARY KEY,
    recipient_user_id   UUID        NOT NULL,
    channel             TEXT        NOT NULL,
    trigger             TEXT        NOT NULL,            -- ALERT_RAISED | ALERT_ESCALATED | INCIDENT_CREATED | SENSOR_OFFLINE | TEST
    dedup_key           TEXT        NOT NULL,
    urgency             TEXT        NOT NULL,            -- LOW | MEDIUM | HIGH | CRITICAL
    subject             TEXT        NOT NULL,            -- one-line operator-facing text
    status              TEXT        NOT NULL,            -- PENDING | SENT | FAILED_RETRYABLE | FAILED | SUPPRESSED
    attempt             INTEGER     NOT NULL DEFAULT 0,
    alert_id            UUID,
    incident_id         UUID,
    track_id            UUID,
    reason              TEXT,                            -- last failure reason, when failed
    provider_message_id TEXT,
    next_attempt_at     TIMESTAMPTZ,                     -- set only while FAILED_RETRYABLE
    correlation_id      TEXT,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    sent_at             TIMESTAMPTZ,
    failed_at           TIMESTAMPTZ,
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    version             BIGINT      NOT NULL DEFAULT 0,
    -- One recipient is notified at most once per deduplication identity.
    CONSTRAINT uq_notification_dedup UNIQUE (recipient_user_id, dedup_key)
);

CREATE INDEX idx_log_recipient ON notification_log (recipient_user_id, created_at DESC);
-- The retry sweep's claim query: due, retryable, oldest first.
CREATE INDEX idx_log_retry ON notification_log (status, next_attempt_at);

CREATE TABLE delivery_receipts (
    id                  BIGSERIAL   PRIMARY KEY,
    notification_id     UUID        NOT NULL REFERENCES notification_log (notification_id) ON DELETE CASCADE,
    attempt             INTEGER     NOT NULL,
    outcome             TEXT        NOT NULL,            -- SENT | FAILED
    channel             TEXT        NOT NULL,
    provider_message_id TEXT,
    reason              TEXT,
    detail              TEXT,
    latency_ms          INTEGER,
    occurred_at         TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_receipts_notification ON delivery_receipts (notification_id, attempt);
