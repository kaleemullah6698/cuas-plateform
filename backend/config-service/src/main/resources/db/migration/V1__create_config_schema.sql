-- config-service schema (ARCHITECTURE.md §4.1.12).
--
-- A namespace is the unit of configuration: it carries a current value and a monotonic version, and
-- every change appends a version (the full value, for point-in-time recall and rollback) and a
-- change-log row (the before -> after diff, the audit trail §4.1.12 requires). The current value, its
-- version history and its change log are written in one transaction, so a change that cannot be
-- recorded does not happen (§7.5, fail-closed).
--
-- The value travels and is stored as canonical JSON text, not JSONB: the service validates structure
-- in the domain and treats the value as opaque at the database, which keeps a namespace's schema out
-- of the DDL where it would otherwise become a migration every time a policy field is added.

CREATE TABLE configurations (
    namespace         VARCHAR(200) PRIMARY KEY,
    kind              VARCHAR(40)  NOT NULL,
    version           BIGINT       NOT NULL,
    value_json        TEXT         NOT NULL,
    deleted           BOOLEAN      NOT NULL DEFAULT FALSE,
    last_actor_id     VARCHAR(64),
    last_justification TEXT,
    updated_at        TIMESTAMPTZ  NOT NULL,
    opt_version       BIGINT       NOT NULL DEFAULT 0
);

CREATE INDEX idx_configurations_kind ON configurations (kind);

CREATE TABLE config_versions (
    id            BIGSERIAL PRIMARY KEY,
    namespace     VARCHAR(200) NOT NULL,
    version       BIGINT       NOT NULL,
    value_json    TEXT         NOT NULL,
    deleted       BOOLEAN      NOT NULL DEFAULT FALSE,
    actor_id      VARCHAR(64),
    justification TEXT,
    created_at    TIMESTAMPTZ  NOT NULL,
    CONSTRAINT uq_config_version UNIQUE (namespace, version)
);

CREATE INDEX idx_config_versions_namespace ON config_versions (namespace, version DESC);

CREATE TABLE config_change_log (
    id            BIGSERIAL PRIMARY KEY,
    namespace     VARCHAR(200) NOT NULL,
    from_version  BIGINT,
    to_version    BIGINT       NOT NULL,
    before_json   TEXT,
    after_json    TEXT         NOT NULL,
    actor_id      VARCHAR(64)  NOT NULL,
    justification TEXT         NOT NULL,
    changed_at    TIMESTAMPTZ  NOT NULL
);

CREATE INDEX idx_config_change_log_namespace ON config_change_log (namespace, changed_at DESC);

-- Feature flags (§4.1.12) have their own table for the M9 gateway's heavily-cached hot-path boolean
-- lookups. It is created here for schema fidelity; M8 manages flags as the FEATURE_FLAG configuration
-- namespace through the standard versioned endpoints, and populating this projection is an M9 item.
CREATE TABLE feature_flags (
    name        VARCHAR(200) PRIMARY KEY,
    enabled     BOOLEAN      NOT NULL DEFAULT FALSE,
    description TEXT,
    updated_at  TIMESTAMPTZ  NOT NULL
);
