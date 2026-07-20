-- Identity and authorisation schema — ARCHITECTURE.md sections 4.1.3, 7.4.
--
-- roles, permissions and role_permissions are reference data: the authoritative definition is the
-- closed Permission/Role enums in platform-commons (section 7.4 declares the matrix fixed).
-- The tables exist so user_roles has referential integrity and an administrator can list them;
-- they are synchronised from the enums at startup rather than seeded here, because duplicating
-- the matrix in SQL is exactly how the two would drift apart.

CREATE TABLE users (
    id            UUID        PRIMARY KEY,
    username      TEXT        NOT NULL UNIQUE,
    email         TEXT        NOT NULL UNIQUE,
    display_name  TEXT        NOT NULL,
    status        TEXT        NOT NULL,          -- ACTIVE | DEACTIVATED
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    version       BIGINT      NOT NULL DEFAULT 0 -- optimistic locking
);

COMMENT ON TABLE users IS 'Platform user accounts. Credentials live in authdb, never here.';

CREATE INDEX idx_users_status ON users (status);

CREATE TABLE permissions (
    code        TEXT PRIMARY KEY,
    description TEXT NOT NULL
);

CREATE TABLE roles (
    code        TEXT PRIMARY KEY,
    description TEXT NOT NULL
);

CREATE TABLE role_permissions (
    role_code       TEXT NOT NULL REFERENCES roles (code) ON DELETE CASCADE,
    permission_code TEXT NOT NULL REFERENCES permissions (code) ON DELETE CASCADE,
    PRIMARY KEY (role_code, permission_code)
);

-- Current role membership only. Who granted a role, when, and why is recorded once in
-- role_change_log (and authoritatively in audit-service); repeating the grantor here would be a
-- second copy of the same fact, free to disagree with the first.
CREATE TABLE user_roles (
    user_id     UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    role_code   TEXT        NOT NULL REFERENCES roles (code),
    assigned_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (user_id, role_code)
);

CREATE INDEX idx_user_roles_role ON user_roles (role_code);

-- Section 7.4: a role change requires a justification, recorded before the change commits.
-- This is the service-local record; the authoritative, tamper-evident copy is in audit-service.
CREATE TABLE role_change_log (
    id            BIGSERIAL   PRIMARY KEY,
    user_id       UUID        NOT NULL,
    role_code     TEXT        NOT NULL,
    change_type   TEXT        NOT NULL,          -- ASSIGNED | REVOKED
    justification TEXT        NOT NULL,
    actor_id      TEXT        NOT NULL,
    occurred_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_role_change_user ON role_change_log (user_id, occurred_at DESC);

-- A role change is history: it is never rewritten.
CREATE RULE role_change_no_update AS ON UPDATE TO role_change_log DO INSTEAD NOTHING;
CREATE RULE role_change_no_delete AS ON DELETE TO role_change_log DO INSTEAD NOTHING;
