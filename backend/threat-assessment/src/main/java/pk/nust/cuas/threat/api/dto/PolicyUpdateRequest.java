package pk.nust.cuas.threat.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import pk.nust.cuas.threat.domain.model.Recommendation;
import pk.nust.cuas.threat.domain.model.ThreatFactorType;
import pk.nust.cuas.threat.domain.model.ThreatLevel;
import pk.nust.cuas.threat.domain.policy.LevelThresholds;
import pk.nust.cuas.threat.domain.policy.NormalisationParameters;
import pk.nust.cuas.threat.domain.policy.RecommendationRule;
import pk.nust.cuas.threat.domain.policy.ReliabilityCondition;
import pk.nust.cuas.threat.domain.policy.ThreatPolicy;

/**
 * A new policy version to publish.
 *
 * <p>Bean Validation covers the shape — present, in range, non-empty. The <em>relationships</em>
 * between fields (weights summing to 1.0, thresholds ascending, rule ids unique) are checked by
 * {@link ThreatPolicy}'s constructor and surface as {@code 422}. That split is deliberate rather
 * than duplicated: the domain invariant has to hold for a policy built any way at all, and
 * re-expressing it in annotations would create a second definition that could drift from the first.
 *
 * @param version       the new immutable version label; must not reuse an existing one
 * @param justification why the change is being made — required, and written to the audit log
 * @param weights       every factor's weight, keyed by factor name; must be complete and sum to 1.0
 * @param thresholdMedium   the score at or above which a track is MEDIUM
 * @param thresholdHigh     the score at or above which a track is HIGH
 * @param thresholdCritical the score at or above which a track is CRITICAL
 * @param proximityCriticalRangeM  the CPA distance scoring 1.0 for proximity
 * @param proximityIgnoreRangeM    the CPA distance beyond which proximity contributes nothing
 * @param closureReferenceMs       the closure rate scoring 1.0
 * @param intentConfirmationSweeps consecutive closing updates at which intent scores 1.0
 * @param reliabilityFloor  the minimum available weight for a score to count as reliable
 * @param rules         the recommendation rules
 */
@Schema(description = "A new threat-scoring policy version to publish")
public record PolicyUpdateRequest(
        @NotBlank @Size(max = 64) String version,
        @NotBlank @Size(max = 1000) String justification,
        @NotEmpty Map<String, @NotNull @DecimalMin("0.0") @DecimalMax("1.0") Double> weights,
        @NotNull @DecimalMin("0.0") @DecimalMax("1.0") Double thresholdMedium,
        @NotNull @DecimalMin("0.0") @DecimalMax("1.0") Double thresholdHigh,
        @NotNull @DecimalMin("0.0") @DecimalMax("1.0") Double thresholdCritical,
        @NotNull @Positive Double proximityCriticalRangeM,
        @NotNull @Positive Double proximityIgnoreRangeM,
        @NotNull @Positive Double closureReferenceMs,
        @NotNull @Min(1) Integer intentConfirmationSweeps,
        @NotNull @DecimalMin("0.0") @DecimalMax("1.0") Double reliabilityFloor,
        @NotEmpty List<@Valid RuleRequest> rules) {

    /**
     * @param policyId    the policy this version belongs to
     * @param effectiveAt when it comes into force
     * @return the domain policy, which validates its own invariants
     * @throws IllegalArgumentException when a field names an unknown factor, level, recommendation
     *         or reliability condition, or when a domain invariant is violated
     */
    public ThreatPolicy toPolicy(String policyId, Instant effectiveAt) {
        return new ThreatPolicy(
                policyId,
                version,
                effectiveAt,
                weights.entrySet().stream().collect(Collectors.toMap(
                        e -> ThreatFactorType.valueOf(e.getKey()), Map.Entry::getValue)),
                new LevelThresholds(thresholdMedium, thresholdHigh, thresholdCritical),
                new NormalisationParameters(proximityCriticalRangeM, proximityIgnoreRangeM,
                        closureReferenceMs, intentConfirmationSweeps),
                reliabilityFloor,
                rules.stream().map(RuleRequest::toRule).toList());
    }

    /**
     * One recommendation rule.
     *
     * <p>{@code recommendation} is a string on the wire but is resolved against the closed
     * {@link Recommendation} enum, so an unrecognised value is rejected at the boundary rather than
     * stored. That is the check that keeps the platform's advisory-only scope limit intact through
     * an API that otherwise lets an administrator rewrite what the system advises.
     *
     * @param ruleId                stable identifier
     * @param priority              evaluation order; lower first
     * @param minLevel              LOW | MEDIUM | HIGH | CRITICAL
     * @param requireClosing        fires only for a track closing on a protected asset
     * @param reliability           ANY | RELIABLE_ONLY | UNRELIABLE_ONLY
     * @param requireUnclassified   fires only while nothing has identified the track
     * @param minConsecutiveClosing fires only after this many consecutive closing updates
     * @param recommendation        one of the five section 6.3.4 advisory values
     * @param rationale             the operator-facing explanation
     */
    @Schema(description = "One recommendation rule")
    public record RuleRequest(
            @NotBlank @Size(max = 64) String ruleId,
            @NotNull Integer priority,
            @NotBlank String minLevel,
            boolean requireClosing,
            @NotBlank String reliability,
            boolean requireUnclassified,
            @Min(0) int minConsecutiveClosing,
            @NotBlank String recommendation,
            @Size(max = 500) String rationale) {

        RecommendationRule toRule() {
            return new RecommendationRule(
                    ruleId, priority,
                    ThreatLevel.valueOf(minLevel),
                    requireClosing,
                    ReliabilityCondition.valueOf(reliability),
                    requireUnclassified,
                    minConsecutiveClosing,
                    Recommendation.valueOf(recommendation),
                    rationale);
        }
    }
}
