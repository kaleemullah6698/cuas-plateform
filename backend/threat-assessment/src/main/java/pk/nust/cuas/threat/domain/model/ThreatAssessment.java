package pk.nust.cuas.threat.domain.model;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * One completed assessment of one track (ARCHITECTURE.md sections 4.1.7, 5.3.3).
 *
 * <p><strong>Immutable, and identified in its own right.</strong> Re-scoring a track produces a new
 * assessment rather than mutating this one. Alerts, incidents and audit records all cite
 * {@code assessmentId}, and a citation whose meaning could change after the fact is not evidence.
 *
 * <p><strong>The level is optional, and that is the point.</strong> A track for which no factor could
 * be evaluated has no threat level — not LOW. Section 2.4.2 renders it as {@code ○UNK}. Modelling it
 * as an absent value rather than a lowest enum constant means no filter can accidentally sweep an
 * unjudged track into the safe bucket.
 *
 * <p>The evidence and the policy version are both recorded because an assessment must be
 * reproducible: given the same inputs and the same policy, scoring must yield the same number
 * months later. Section 6.3.4 requires a defence-adjacent score to be defensible to a reviewer, and
 * a number nobody can recompute is not.
 *
 * @param assessmentId       UUIDv7 — time-sortable
 * @param trackId            the track scored
 * @param sectorId           its sector
 * @param compositeScore     the weighted sum of the available factors, in {@code [0,1]}
 * @param level              the level that score falls in, or empty when nothing could be evaluated
 * @param factors            every factor the policy defines, available or not
 * @param confidence         how much of the model had evidence behind it
 * @param recommendation     what the rules engine advised
 * @param policyId           the policy in force
 * @param policyVersion      its immutable version — what makes this score reproducible
 * @param policyEffectiveAt  when that version came into force
 * @param evidence           the inputs, stored so the score can be recomputed from the record
 * @param inputsHash         content hash of the inputs; the idempotency key for redelivery
 * @param assessedAt         platform clock at scoring
 * @param observedAt         the track estimate time this assessment scored
 * @param correlationId      end-to-end trace, carried from the triggering event
 */
public record ThreatAssessment(
        UUID assessmentId,
        UUID trackId,
        String sectorId,
        double compositeScore,
        Optional<ThreatLevel> level,
        List<ThreatFactor> factors,
        AssessmentConfidence confidence,
        AdvisoryRecommendation recommendation,
        String policyId,
        String policyVersion,
        Instant policyEffectiveAt,
        TrackEvidence evidence,
        String inputsHash,
        Instant assessedAt,
        Instant observedAt,
        String correlationId) {

    public ThreatAssessment {
        if (assessmentId == null || trackId == null) {
            throw new IllegalArgumentException("An assessment needs its own id and a track id");
        }
        if (level == null || factors == null || confidence == null || recommendation == null) {
            throw new IllegalArgumentException(
                    "An assessment needs a level, factors, a confidence summary and a recommendation");
        }
        if (compositeScore < 0.0 || compositeScore > 1.0 || Double.isNaN(compositeScore)) {
            throw new IllegalArgumentException(
                    "Composite score must be in [0,1], was " + compositeScore);
        }
        if (assessedAt == null || observedAt == null || policyEffectiveAt == null) {
            throw new IllegalArgumentException("An assessment needs its clocks and its policy time");
        }
        if (policyVersion == null || policyVersion.isBlank()) {
            throw new IllegalArgumentException(
                    "An assessment must record the policy version that produced it, or it cannot be"
                            + " reproduced");
        }
        if (level.isEmpty() != confidence.isEmpty()) {
            throw new IllegalArgumentException(
                    "A level is present exactly when at least one factor was evaluable; an"
                            + " unassessed track must not carry a level, and an assessed one must");
        }
        factors = List.copyOf(factors);
        sectorId = sectorId == null ? "" : sectorId;
        correlationId = correlationId == null ? "" : correlationId;
        inputsHash = inputsHash == null ? "" : inputsHash;
    }

    /** @return {@code true} when at least one factor could be evaluated */
    public boolean isAssessed() {
        return level.isPresent();
    }

    /**
     * @param type the factor to look up
     * @return that factor, or empty when the policy that produced this assessment did not define it
     */
    public Optional<ThreatFactor> factor(ThreatFactorType type) {
        return factors.stream().filter(f -> f.type() == type).findFirst();
    }
}
