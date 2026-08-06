package pk.nust.cuas.threat.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import pk.nust.cuas.threat.domain.model.ThreatAssessment;

/**
 * A track's current threat assessment, without the per-factor breakdown.
 *
 * <p>{@code level} is null for a track nothing could be evaluated for — the {@code UNK} of section
 * 2.4.2. It is deliberately not reported as {@code LOW}: a client filtering on level must be able to
 * tell an unjudged track from a harmless one.
 *
 * @param assessmentId   the assessment's own id
 * @param trackId        the track scored
 * @param sectorId       its sector
 * @param compositeScore the weighted composite, in [0,1]
 * @param level          LOW | MEDIUM | HIGH | CRITICAL, or null when unassessed
 * @param recommendation the advisory recommendation
 * @param rationale      why it was advised
 * @param ruleId         the rule that fired
 * @param availableWeight how much of the model had evidence, in [0,1]
 * @param factorsAvailable how many factors had evidence
 * @param factorsTotal   how many the policy defines
 * @param reliable       whether the score met the policy's reliability floor
 * @param policyId       the policy in force
 * @param policyVersion  its immutable version — what makes this score reproducible
 * @param assessedAt     platform clock at scoring
 * @param observedAt     the track estimate time that was scored
 */
@Schema(description = "A composite threat assessment for one track")
public record ThreatAssessmentResponse(
        String assessmentId,
        String trackId,
        String sectorId,
        double compositeScore,
        String level,
        String recommendation,
        String rationale,
        String ruleId,
        double availableWeight,
        int factorsAvailable,
        int factorsTotal,
        boolean reliable,
        String policyId,
        String policyVersion,
        Instant assessedAt,
        Instant observedAt) {

    /**
     * @param assessment the domain assessment
     * @return its wire form
     */
    public static ThreatAssessmentResponse from(ThreatAssessment assessment) {
        return new ThreatAssessmentResponse(
                assessment.assessmentId().toString(),
                assessment.trackId().toString(),
                assessment.sectorId(),
                assessment.compositeScore(),
                assessment.level().map(Enum::name).orElse(null),
                assessment.recommendation().recommendation().name(),
                assessment.recommendation().rationale(),
                assessment.recommendation().ruleId(),
                assessment.confidence().availableWeight(),
                assessment.confidence().factorsAvailable(),
                assessment.confidence().factorsTotal(),
                assessment.confidence().isReliable(),
                assessment.policyId(),
                assessment.policyVersion(),
                assessment.assessedAt(),
                assessment.observedAt());
    }
}
