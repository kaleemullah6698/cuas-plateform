-- Threat assessment schema — ARCHITECTURE.md sections 4.1.7, 5.3.3, 6.3.4.
--
--   threat_policies    the policy identity; one row per policy, pointing at its active version
--   policy_versions    immutable versions: weights, thresholds, normalisation, reliability floor
--   rule_definitions   the recommendation rules belonging to one policy version
--   assessments        every score ever produced, with the evidence and policy that produced it
--   threat_scores      the current level per track — what the read API answers from
--
-- Section 4.1.7 names exactly these five tables and no others; the schema below adds none.
--
-- The whole design turns on one property: a published policy version is immutable. Every
-- assessment cites the version that produced it, so a version that could be edited in place would
-- silently re-describe every past score under today's thresholds — and a threat score that cannot
-- be reproduced cannot be defended to a reviewer, which section 6.3.4 requires it to be. Changing
-- a policy therefore inserts a new version and repoints threat_policies.active_version.

CREATE TABLE threat_policies (
    policy_id      TEXT        PRIMARY KEY,
    name           TEXT        NOT NULL,
    description    TEXT        NOT NULL DEFAULT '',
    active_version TEXT        NOT NULL,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ NOT NULL DEFAULT now()
);

COMMENT ON TABLE threat_policies IS
    'Policy identity and which of its versions is in force. Versions themselves are immutable.';

CREATE TABLE policy_versions (
    id             BIGSERIAL   PRIMARY KEY,
    policy_id      TEXT        NOT NULL REFERENCES threat_policies (policy_id),
    version        TEXT        NOT NULL,
    effective_at   TIMESTAMPTZ NOT NULL,

    -- The five section 6.3.4 factor weights. Stored as columns rather than a key/value child
    -- table: the factor set is closed by specification, so a fixed shape is the honest one, and a
    -- missing weight becomes a NOT NULL violation at write time instead of a silently dropped
    -- factor at scoring time. Adding a sixth factor is an architecture amendment, and it correctly
    -- requires a migration here.
    weight_model_confidence  DOUBLE PRECISION NOT NULL,
    weight_proximity         DOUBLE PRECISION NOT NULL,
    weight_closing_velocity  DOUBLE PRECISION NOT NULL,
    weight_trajectory_intent DOUBLE PRECISION NOT NULL,
    weight_sensor_agreement  DOUBLE PRECISION NOT NULL,

    -- Level boundaries (section 6.3.4, amended M7.A). Lower-inclusive, upper-exclusive.
    threshold_medium   DOUBLE PRECISION NOT NULL,
    threshold_high     DOUBLE PRECISION NOT NULL,
    threshold_critical DOUBLE PRECISION NOT NULL,

    -- Normalisation curves: what "close" and "fast" mean for this deployment.
    proximity_critical_range_m DOUBLE PRECISION NOT NULL,
    proximity_ignore_range_m   DOUBLE PRECISION NOT NULL,
    closure_reference_ms       DOUBLE PRECISION NOT NULL,
    intent_confirmation_sweeps INTEGER          NOT NULL,

    reliability_floor DOUBLE PRECISION NOT NULL,

    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),

    -- A version label identifies one immutable configuration for the life of the deployment.
    CONSTRAINT uq_policy_version UNIQUE (policy_id, version),
    CONSTRAINT ck_thresholds_ascending CHECK (
        threshold_medium > 0.0
        AND threshold_medium < threshold_high
        AND threshold_high < threshold_critical
        AND threshold_critical < 1.0),
    -- Enforced here as well as in ThreatPolicy's constructor. The domain check is the one that
    -- produces a good error message; this one is what makes the invariant true of the data even if
    -- a row is ever inserted by something other than this service.
    CONSTRAINT ck_weights_sum_to_one CHECK (
        abs((weight_model_confidence + weight_proximity + weight_closing_velocity
             + weight_trajectory_intent + weight_sensor_agreement) - 1.0) < 1e-6)
);

CREATE INDEX idx_policy_versions_policy ON policy_versions (policy_id, effective_at DESC);

CREATE TABLE rule_definitions (
    id                      BIGSERIAL PRIMARY KEY,
    policy_version_id       BIGINT    NOT NULL REFERENCES policy_versions (id) ON DELETE CASCADE,
    rule_id                 TEXT      NOT NULL,
    priority                INTEGER   NOT NULL,
    min_level               TEXT      NOT NULL,   -- LOW | MEDIUM | HIGH | CRITICAL
    require_closing         BOOLEAN   NOT NULL DEFAULT FALSE,
    reliability             TEXT      NOT NULL,   -- ANY | RELIABLE_ONLY | UNRELIABLE_ONLY
    require_unclassified    BOOLEAN   NOT NULL DEFAULT FALSE,
    min_consecutive_closing INTEGER   NOT NULL DEFAULT 0,
    -- Constrained to the closed section 6.3.4 vocabulary at the database boundary as well as in
    -- the enum. Rules are data, and this is what stops a hand-written INSERT from introducing an
    -- instruction the platform's advisory-only scope limit does not permit — the one place a new
    -- kind of instruction could otherwise enter without passing a code review.
    recommendation          TEXT      NOT NULL,
    rationale               TEXT      NOT NULL DEFAULT '',

    CONSTRAINT uq_rule_per_version UNIQUE (policy_version_id, rule_id),
    CONSTRAINT ck_rule_recommendation CHECK (recommendation IN (
        'MONITOR', 'TRACK_AND_MONITOR', 'REQUEST_VISUAL_ID',
        'ESCALATE_TO_SUPERVISOR', 'RECOMMEND_OPERATOR_REVIEW')),
    CONSTRAINT ck_rule_min_level CHECK (min_level IN ('LOW', 'MEDIUM', 'HIGH', 'CRITICAL')),
    CONSTRAINT ck_rule_reliability CHECK (reliability IN (
        'ANY', 'RELIABLE_ONLY', 'UNRELIABLE_ONLY'))
);

CREATE INDEX idx_rule_definitions_version ON rule_definitions (policy_version_id, priority);

CREATE TABLE assessments (
    assessment_id  UUID        PRIMARY KEY,       -- UUIDv7, time-sortable
    track_id       UUID        NOT NULL,
    sector_id      TEXT        NOT NULL DEFAULT '',

    composite_score DOUBLE PRECISION NOT NULL,
    -- Null means unassessed, not LOW. A track no factor could be evaluated for has no level;
    -- section 2.4.2 renders it as UNK. Storing a level here for such a track would let the first
    -- "WHERE level = 'LOW'" query written against this table read it as harmless.
    level          TEXT,

    recommendation TEXT        NOT NULL,
    rule_id        TEXT        NOT NULL,
    rationale      TEXT        NOT NULL DEFAULT '',

    policy_id           TEXT        NOT NULL,
    policy_version      TEXT        NOT NULL,
    policy_effective_at TIMESTAMPTZ NOT NULL,

    available_weight  DOUBLE PRECISION NOT NULL,
    factors_available INTEGER          NOT NULL,
    factors_total     INTEGER          NOT NULL,
    is_reliable       BOOLEAN          NOT NULL,

    -- The per-factor breakdown and the inputs it was computed from. JSONB rather than child
    -- tables: neither is ever queried independently — the breakdown is read whole as section
    -- 2.4.2's panel, and the evidence is read whole to re-score — so a join per assessment would
    -- buy nothing, and section 4.1.7 names five tables in this database.
    factors  JSONB NOT NULL,
    evidence JSONB NOT NULL,

    -- Content hash of (evidence, policy version). Kafka delivers at least once, so the same track
    -- update can be processed twice; identical inputs under an identical policy give an identical
    -- answer, and the unique constraint below turns the second attempt into a skip rather than a
    -- duplicate assessment, a duplicate level transition, and a duplicate alert downstream.
    inputs_hash TEXT NOT NULL,

    assessed_at    TIMESTAMPTZ NOT NULL,
    observed_at    TIMESTAMPTZ NOT NULL,
    correlation_id TEXT        NOT NULL DEFAULT '',
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT uq_assessment_inputs UNIQUE (track_id, inputs_hash),
    CONSTRAINT ck_assessment_score CHECK (composite_score >= 0.0 AND composite_score <= 1.0),
    CONSTRAINT ck_assessment_level CHECK (level IS NULL OR level IN ('LOW','MEDIUM','HIGH','CRITICAL')),
    CONSTRAINT ck_assessment_recommendation CHECK (recommendation IN (
        'MONITOR', 'TRACK_AND_MONITOR', 'REQUEST_VISUAL_ID',
        'ESCALATE_TO_SUPERVISOR', 'RECOMMEND_OPERATOR_REVIEW')),
    -- A level exists exactly when at least one factor was evaluable.
    CONSTRAINT ck_assessment_level_iff_evaluated CHECK (
        (level IS NULL) = (factors_available = 0))
);

COMMENT ON TABLE assessments IS
    'Immutable history: every score, with the evidence and policy version that produced it.';

CREATE INDEX idx_assessments_track_assessed ON assessments (track_id, assessed_at DESC);
CREATE INDEX idx_assessments_policy_version ON assessments (policy_version);

CREATE TABLE threat_scores (
    track_id            UUID        PRIMARY KEY,
    latest_assessment_id UUID       NOT NULL REFERENCES assessments (assessment_id),
    sector_id           TEXT        NOT NULL DEFAULT '',
    level               TEXT,                      -- null while unassessed, as on assessments
    composite_score     DOUBLE PRECISION NOT NULL,
    recommendation      TEXT        NOT NULL,
    assessed_at         TIMESTAMPTZ NOT NULL,
    observed_at         TIMESTAMPTZ NOT NULL,
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT ck_score_level CHECK (level IS NULL OR level IN ('LOW','MEDIUM','HIGH','CRITICAL'))
);

COMMENT ON TABLE threat_scores IS
    'Current level per track: what the read API answers from and what a transition is measured '
    'against. Derived from assessments, kept separate so neither is an ordering query per event.';

CREATE INDEX idx_threat_scores_level ON threat_scores (level, assessed_at DESC);
CREATE INDEX idx_threat_scores_sector ON threat_scores (sector_id, assessed_at DESC);
