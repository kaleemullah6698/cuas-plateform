-- Authentication schema — ARCHITECTURE.md sections 4.1.2, 7.3.
--
-- Holds credentials, MFA secrets, refresh-token families and sessions. Roles and permissions live
-- in userdb: this service proves who a caller is, never what they may do.

CREATE TABLE credentials (
    user_id         UUID        PRIMARY KEY,
    username        TEXT        NOT NULL UNIQUE,
    -- Argon2id encoding, including its parameters, so a future parameter change can be detected
    -- per-row and the hash upgraded on next successful login rather than all at once.
    password_hash   TEXT        NOT NULL,
    failed_attempts INT         NOT NULL DEFAULT 0,
    locked_until    TIMESTAMPTZ,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

COMMENT ON COLUMN credentials.password_hash IS
    'Argon2id (section 7.3: m=64MB, t=3, p=4). Never a bare digest, never reversible.';

CREATE TABLE mfa_secrets (
    user_id      UUID        PRIMARY KEY REFERENCES credentials (user_id) ON DELETE CASCADE,
    secret       TEXT        NOT NULL,     -- base32 TOTP shared secret
    enabled      BOOLEAN     NOT NULL DEFAULT false,
    -- Highest TOTP time-step already accepted. A code is single-use: replaying one within its
    -- validity window must fail, which a timestamp alone cannot express (section 7.3).
    last_used_step BIGINT,
    confirmed_at TIMESTAMPTZ,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Short-lived challenge issued after a correct password, consumed by MFA verification. Keeping it
-- server-side means the client cannot skip the second factor by fabricating state.
CREATE TABLE mfa_challenges (
    id          UUID        PRIMARY KEY,
    user_id     UUID        NOT NULL REFERENCES credentials (user_id) ON DELETE CASCADE,
    issued_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    expires_at  TIMESTAMPTZ NOT NULL,
    consumed_at TIMESTAMPTZ
);

CREATE INDEX idx_mfa_challenge_user ON mfa_challenges (user_id, expires_at DESC);

-- Rotating refresh tokens (section 7.3). A family is one login; each refresh mints a new member
-- and retires the previous one. Presenting a retired member means the token was captured, so the
-- whole family is revoked — that is the reuse detection.
CREATE TABLE refresh_tokens (
    id          UUID        PRIMARY KEY,
    family_id   UUID        NOT NULL,
    user_id     UUID        NOT NULL REFERENCES credentials (user_id) ON DELETE CASCADE,
    -- Only the digest is stored: a database disclosure must not yield usable tokens.
    token_hash  TEXT        NOT NULL UNIQUE,
    session_id  UUID        NOT NULL,
    issued_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    expires_at  TIMESTAMPTZ NOT NULL,
    used_at     TIMESTAMPTZ,
    revoked_at  TIMESTAMPTZ,
    revoked_reason TEXT
);

CREATE INDEX idx_refresh_family ON refresh_tokens (family_id);
CREATE INDEX idx_refresh_user   ON refresh_tokens (user_id);
CREATE INDEX idx_refresh_expiry ON refresh_tokens (expires_at);

CREATE TABLE sessions (
    id           UUID        PRIMARY KEY,
    user_id      UUID        NOT NULL REFERENCES credentials (user_id) ON DELETE CASCADE,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    last_seen_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    revoked_at   TIMESTAMPTZ,
    user_agent   TEXT,
    ip           INET
);

CREATE INDEX idx_sessions_user ON sessions (user_id, revoked_at);

-- Every attempt, successful or not: section 7.5 makes authentication attempts mandatory audit
-- events, and lockout decisions need the local history.
CREATE TABLE login_attempts (
    id           BIGSERIAL   PRIMARY KEY,
    username     TEXT        NOT NULL,
    ip           INET,
    succeeded    BOOLEAN     NOT NULL,
    failure_reason TEXT,
    attempted_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_login_attempts_username ON login_attempts (username, attempted_at DESC);

-- Attempt history is evidence and is never rewritten.
CREATE RULE login_attempts_no_update AS ON UPDATE TO login_attempts DO INSTEAD NOTHING;
CREATE RULE login_attempts_no_delete AS ON DELETE TO login_attempts DO INSTEAD NOTHING;
