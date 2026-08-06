package pk.nust.cuas.threat.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import pk.nust.cuas.threat.domain.policy.RecommendationRule;
import pk.nust.cuas.threat.domain.policy.ThreatPolicy;

/**
 * A scoring policy version as returned by {@code GET /threats/policies}.
 *
 * <p>Everything that decides what a score means is here, which is the point: an operator who
 * disagrees with a threat level must be able to see the numbers that produced it without reading the
 * source.
 *
 * @param policyId          the policy's stable identity
 * @param version           the immutable version label recorded on every assessment
 * @param effectiveAt       when it came into force
 * @param weights           the five factor weights; they sum to 1.0
 * @param thresholdMedium   the score at or above which a track is MEDIUM
 * @param thresholdHigh     the score at or above which a track is HIGH
 * @param thresholdCritical the score at or above which a track is CRITICAL
 * @param proximityCriticalRangeM  the CPA distance scoring 1.0 for proximity
 * @param proximityIgnoreRangeM    the CPA distance beyond which proximity contributes nothing
 * @param closureReferenceMs       the closure rate scoring 1.0
 * @param intentConfirmationSweeps consecutive closing updates at which intent scores 1.0
 * @param reliabilityFloor  the minimum available weight for a score to count as reliable
 * @param rules             the recommendation rules, in priority order
 */
@Schema(description = "A versioned threat-scoring policy")
public record PolicyResponse(
        String policyId,
        String version,
        Instant effectiveAt,
        Map<String, Double> weights,
        double thresholdMedium,
        double thresholdHigh,
        double thresholdCritical,
        double proximityCriticalRangeM,
        double proximityIgnoreRangeM,
        double closureReferenceMs,
        int intentConfirmationSweeps,
        double reliabilityFloor,
        List<RuleResponse> rules) {

    /**
     * @param policy the domain policy
     * @return its wire form
     */
    public static PolicyResponse from(ThreatPolicy policy) {
        return new PolicyResponse(
                policy.policyId(),
                policy.version(),
                policy.effectiveAt(),
                policy.weights().entrySet().stream().collect(Collectors.toMap(
                        e -> e.getKey().name(), Map.Entry::getValue)),
                policy.thresholds().medium(),
                policy.thresholds().high(),
                policy.thresholds().critical(),
                policy.normalisation().proximityCriticalRangeM(),
                policy.normalisation().proximityIgnoreRangeM(),
                policy.normalisation().closureReferenceMs(),
                policy.normalisation().intentConfirmationSweeps(),
                policy.reliabilityFloor(),
                policy.rules().stream().map(RuleResponse::from).toList());
    }

    /**
     * One recommendation rule.
     *
     * @param ruleId                stable identifier, recorded on every assessment it produced
     * @param priority              evaluation order; lower first, first match wins
     * @param minLevel              fires at or above this level
     * @param requireClosing        fires only for a track closing on a protected asset
     * @param reliability           ANY | RELIABLE_ONLY | UNRELIABLE_ONLY
     * @param requireUnclassified   fires only while nothing has identified the track
     * @param minConsecutiveClosing fires only after this many consecutive closing updates
     * @param recommendation        the advisory value, from the closed section 6.3.4 vocabulary
     * @param rationale             the operator-facing explanation
     */
    @Schema(description = "One recommendation rule of a policy version")
    public record RuleResponse(
            String ruleId,
            int priority,
            String minLevel,
            boolean requireClosing,
            String reliability,
            boolean requireUnclassified,
            int minConsecutiveClosing,
            String recommendation,
            String rationale) {

        static RuleResponse from(RecommendationRule rule) {
            return new RuleResponse(
                    rule.ruleId(), rule.priority(), rule.minLevel().name(), rule.requireClosing(),
                    rule.reliability().name(), rule.requireUnclassified(),
                    rule.minConsecutiveClosing(), rule.recommendation().name(), rule.rationale());
        }
    }
}
