-- Tamper-evident audit log — ARCHITECTURE.md section 7.5.
--
-- The table definition, indexes, and immutability rules below are reproduced from section 7.5.
-- Immutability is enforced by the database, not by application code: an application-level
-- guarantee is not evidence, because the same process that writes the record could rewrite it.

CREATE TABLE audit_log (
    seq             BIGSERIAL PRIMARY KEY,
    audit_id        UUID        NOT NULL UNIQUE,
    occurred_at     TIMESTAMPTZ NOT NULL,
    recorded_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    actor_id        TEXT        NOT NULL,
    actor_type      TEXT        NOT NULL,   -- USER | SERVICE | SYSTEM
    actor_ip        INET,
    session_id      TEXT,
    action          TEXT        NOT NULL,   -- ALERT_ACKNOWLEDGED, POLICY_UPDATED, …
    resource_type   TEXT        NOT NULL,
    resource_id     TEXT,
    outcome         TEXT        NOT NULL,   -- SUCCESS | FAILURE | DENIED
    details         JSONB       NOT NULL DEFAULT '{}',
    correlation_id  TEXT,
    prev_hash       BYTEA       NOT NULL,
    record_hash     BYTEA       NOT NULL
);

COMMENT ON TABLE  audit_log IS
    'Append-only, hash-chained audit trail (ARCHITECTURE.md 7.5). Never updated or deleted.';
COMMENT ON COLUMN audit_log.audit_id IS
    'Idempotency key: a duplicate delivery is rejected by the UNIQUE constraint.';
COMMENT ON COLUMN audit_log.prev_hash IS
    'record_hash of the preceding row; 32 zero bytes for the genesis record.';
COMMENT ON COLUMN audit_log.record_hash IS
    'SHA-256 over prev_hash and this row''s canonical content.';

-- Indexes (section 7.5): the three access paths an auditor actually uses.
CREATE INDEX idx_audit_actor_time  ON audit_log (actor_id, occurred_at DESC);
CREATE INDEX idx_audit_resource    ON audit_log (resource_type, resource_id, occurred_at DESC);
CREATE INDEX idx_audit_correlation ON audit_log (correlation_id);

-- Immutability enforced at the database, not the application (section 7.5).
-- DO INSTEAD NOTHING silently discards any UPDATE or DELETE, including one issued by the
-- table owner, so no code path in the platform can alter a recorded row.
CREATE RULE audit_no_update AS ON UPDATE TO audit_log DO INSTEAD NOTHING;
CREATE RULE audit_no_delete AS ON DELETE TO audit_log DO INSTEAD NOTHING;

-- Section 7.5 additionally revokes write privileges from the least-privilege application role.
-- That role exists in deployed environments but not necessarily in a local or test database,
-- so the revoke is applied only when present rather than failing the migration.
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'cuas_app') THEN
        REVOKE UPDATE, DELETE, TRUNCATE ON audit_log FROM cuas_app;
    END IF;
END
$$;

-- Chain checkpoints (section 7.5): every 10,000 records and every 24 hours the chain head is
-- checkpointed and signed, so verifying an arbitrary range only requires re-hashing the segment
-- since the preceding checkpoint rather than the entire log.
CREATE TABLE audit_chain_checkpoint (
    id              BIGSERIAL   PRIMARY KEY,
    head_seq        BIGINT      NOT NULL UNIQUE REFERENCES audit_log (seq),
    head_hash       BYTEA       NOT NULL,
    record_count    BIGINT      NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    signature       BYTEA,                 -- Ed25519 over head_hash; null until signing is configured
    signing_key_id  TEXT
);

COMMENT ON TABLE audit_chain_checkpoint IS
    'Signed chain-head checkpoints bounding the cost of range verification (ARCHITECTURE.md 7.5).';

CREATE INDEX idx_audit_checkpoint_head ON audit_chain_checkpoint (head_seq DESC);

CREATE RULE checkpoint_no_update AS ON UPDATE TO audit_chain_checkpoint DO INSTEAD NOTHING;
CREATE RULE checkpoint_no_delete AS ON DELETE TO audit_chain_checkpoint DO INSTEAD NOTHING;
