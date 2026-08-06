# threat-assessment

Composite threat scoring, the rules engine, and advisory recommendations.

- **Port:** 8086 (HTTP)
- **Database:** `threatdb`
- **Specification:** `docs/ARCHITECTURE.md` §4.1.7, §5.3.3, §6.3.4, §2.4.2

## The two rules this service exists to keep

**Every recommendation is advisory.** The output vocabulary is §6.3.4's closed five — `MONITOR`,
`TRACK_AND_MONITOR`, `REQUEST_VISUAL_ID`, `ESCALATE_TO_SUPERVISOR`, `RECOMMEND_OPERATOR_REVIEW`. No
code path here can emit an engagement action, and `RecommendationVocabularyTest` fails the build if a
sixth value appears. The rules are stored as *data*, so the same constraint is enforced again at the
database boundary (`ck_rule_recommendation`) and at the API boundary — otherwise configuration could
introduce a new kind of instruction where no code review would ever see it.

**A missing factor is reported, never defaulted.** §6.3.4 requires it, and the type system enforces
it: `ThreatFactor` holds either a value or a stated reason it has none, and the constructor rejects
both-or-neither. A track outside every asset's prediction horizon has no proximity evidence — a 0.0
would read as "very far" and a 1.0 as "on top of the asset", and both are claims the system cannot
support. The shortfall surfaces as `AssessmentConfidence.availableWeight` instead of hiding inside a
plausible-looking number.

## The rules engine is here, in Track A

§6.3.4 describes `threat-scorer` as "a weighted rules engine + gradient-boosted residual", which
reads as though the whole scorer were an AI service. §4.1.7 settles it, and the split is the reason
this service works at all:

- The **weighted rules engine is Java in this module.** Deterministic, explainable, and the source of
  §2.4.2's breakdown panel.
- The **residual is Track B**, and reaches this service as an ordinary `ai.inference.completed.v1`
  event weighed as *one factor of five*. Never a synchronous call.

There is **no gRPC client in this module**, and `LayeringTest` fails the build if one appears.
`track-correlation → ai-orchestrator` stays the pipeline's only synchronous link (CLAUDE.md §2), and
the consequence is that this service scores, levels and advises with **zero models present** — the
same property §6.4 gives the rest of Track A. A threat picture that went blank when the AI boundary
degraded would invert §5.6: a drone scored late is a degraded outcome, a drone never scored is an
undetected one.

## Scoring

Five factors, weighted per §6.3.4, summing to 1.00 — so the composite is in `[0,1]` by construction
rather than by a clamp that would silently absorb a misconfigured weight.

| Factor | Weight | Computed from | Absent when |
|---|---|---|---|
| Model confidence | 0.40 | the most confident `ai.inference.completed.v1` verdict | nothing has classified the track |
| Proximity | 0.25 | CPA distance, linear ramp between two policy ranges | no asset within the prediction horizon |
| Closing velocity | 0.20 | derived closure rate (below) | no approach geometry at all |
| Trajectory intent | 0.10 | consecutive updates the track has kept closing | fewer than two such updates |
| Sensor agreement | 0.05 | fraction of reporting services agreeing on the label | fewer than two services have reported |

**Closure rate is derived, not substituted.** The track contract carries the CPA geometry and the
ground speed but no instantaneous range rate. Under the same constant-velocity geometry the CPA
itself assumes, a track at `v` m/s covers `v × t` metres before a closest approach of `d`, so its
present separation is `hypot(d, v·t)` and the closure rate is the separation lost per second. Using
ground speed directly would have been the obvious shortcut and would overstate closure by more than
double for a track passing *across* an asset rather than *at* it.

**A receding track scores zero closure and the factor stays available.** "We measured it and it is
moving away" is evidence, and a strong one. Only "there is no approach geometry" is absence.

**Intent needs more than one sample**, and the streak *resets* rather than pausing when a track turns
away — a track that closes, turns, and closes again has not demonstrated the same thing as one that
never turned.

**One service cannot corroborate itself**, and only the latest result per service is kept, so a
talkative model cannot manufacture its own agreement.

## Levels and the one boundary the specification fixed

| Level | Composite |
|---|---|
| LOW | `< 0.30` |
| MEDIUM | `0.30 – 0.55` |
| HIGH | `0.55 – 0.80` |
| CRITICAL | `≥ 0.80` |

**Only `CRITICAL ≥ 0.80` comes from §2.4.2.** The other two are a judgement — bands of comparable
width below that anchor — and are expected to be retuned per deployment, which is exactly why they
are policy rows. §2.4.2's console `CONF` column is *classification confidence*, not composite score,
and constrains nothing here.

**An unassessed track has no level at all.** Not LOW. `ThreatAssessment.level()` is an `Optional`,
the column is nullable, and the contract maps it to `THREAT_LEVEL_UNSPECIFIED` — §2.4.2's `○UNK`. An
enum constant would be sortable alongside the real levels, and the first `level <= LOW` filter
written against it would let an unjudged track read as the safest thing on the operator's screen.

## Policy

Everything that decides what a score *means* is data: weights, thresholds, normalisation curves, the
reliability floor, and the recommendation rules. Nothing that decides what a score means is in code.

**A published version is immutable.** Editing a policy writes a *new* version and repoints
`threat_policies.active_version`; the old rows stay exactly as written. Every assessment cites the
version that produced it, so a mutable version would silently re-describe last month's scores under
this month's thresholds — and a score that cannot be reproduced cannot be defended to a reviewer,
which §6.3.4 requires it to be.

Rules are evaluated in priority order, first match wins. Their conditions are a closed set
(`RuleContext`) — there is no expression language and no way for a stored rule to reach a fact that
record does not carry. A general expression language over the whole assessment would put this
platform's advisory logic somewhere no code review ever looks.

## What reaches Kafka

| Topic | Key | When |
|---|---|---|
| `threat.assessed.v1` | `trackId` | every score |
| `threat.recommendation.generated.v1` | `trackId` | every score |
| `threat.level.changed.v1` | `trackId` | **only when the level actually moves** |

All three are keyed by track id, including the recommendation, so a track's assessments, advice and
transitions share a partition and stay ordered relative to each other.

**Transitions fire on change only.** Alert-service raises an alert from each one (§4.1.8); a level
republished unchanged at the track-update rate would raise one alert per update — the duplicate-alert
failure §5.7 names as the fastest way to destroy operator trust.

**A track that becomes *unassessed* does not report a de-escalation.** Losing evidence is not a
threat receding, and announcing it as one would tell alert-service to close an alert on the strength
of the system knowing *less* than it did before.

**Publication is synchronous and failure propagates**, unlike the orchestrator's logged-and-dropped
sends. There the caller already holds its answer and the event is telemetry; here the event *is* the
output, and a stored assessment nothing downstream hears about is a threat nobody is alerted to.

## Idempotency

Kafka delivers at least once. The guard is a **content hash** over the evidence plus the policy
version, unique-constrained on `(track_id, inputs_hash)`. Keying on `(trackId, observedAt)` would be
the obvious choice and wrong in the other direction: a new inference for an unchanged track estimate
is a genuinely different question and shares that key exactly. A duplicate is *reported*, not thrown
— at-least-once makes it an expected event, and a caller forced to catch an exception for the normal
path will eventually catch it too broadly.

## Evidence, and why it lives on the assessment

The two feeding topics arrive independently, so a pass triggered by either must recover the other's
contribution. That state is persisted **with the assessment it produced**, in a `jsonb` column,
rather than in a separate cache — the assessment already has to store its inputs to be reproducible,
and a second store would be one more thing to keep coherent for no additional fact.

## API

| Endpoint | Permission | Notes |
|---|---|---|
| `GET /threats/{trackId}` | `track:read` | 404 when never scored — not a zero score |
| `GET /threats/{trackId}/breakdown` | `track:read` | §2.4.2's explainability panel |
| `GET /threats/policies` | `policy:read` | every version, newest first |
| `PUT /threats/policies` | `policy:write` | new immutable version; audited fail-closed |
| `POST /threats/evaluate` | `policy:read` | what-if; persists and publishes nothing |

**No new permission was invented.** §7.4's matrix is closed, so a threat score — a fact about a track
— reads under `track:read`, and the policy surface uses `policy:read`/`policy:write`. §7.4 grants
`policy:write` to ADMIN alone, which is right for an endpoint that changes what the system concludes
about every track in the airspace.

**The what-if is a read.** Requiring `policy:write` would mean an operator had to be able to *change*
a policy in order to ask what one would do.

**A policy change that cannot be audited does not happen** (§7.5). The audit record is written inside
the transaction and before the version becomes active; an `AuditWriteException` rolls the change back
and surfaces as `503`, not `500` — the request was valid and should be retried once the log is
reachable.

## Status

**M7.B complete.** 116 module tests. Scoring, levelling, the rules engine, `threatdb` with immutable
policy versioning, both consumers, all three topics, and the four §4.1.7 endpoints.

**Deferred with cause:** `platform.config.policy.updated.v1` is listed in §4.1.7 as consumed, but
config-service does not exist until M8 — `PUT /threats/policies` is the interim source, exactly as
the orchestrator's admin API is for `platform.config.ai.updated.v1`. Consequently a policy published
on one instance reaches the others on their next restart; a polling loop short enough to be useful
would reintroduce the per-assessment query the policy cache exists to remove, and one long enough to
be cheap would give a false impression of promptness.
