package pk.nust.cuas.threat.domain.policy;

import java.time.Instant;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import pk.nust.cuas.threat.domain.model.Recommendation;
import pk.nust.cuas.threat.domain.model.ThreatFactorType;
import pk.nust.cuas.threat.domain.model.ThreatLevel;

/**
 * A complete, immutable scoring policy (ARCHITECTURE.md sections 4.1.7, 6.3.4).
 *
 * <p>Everything that decides what a score means lives here: the factor weights, the level
 * thresholds, the normalisation curves, the reliability floor, and the recommendation rules. Nothing
 * that decides what a score means lives in code, which is the property that makes a retune a policy
 * revision with an audit trail rather than a release.
 *
 * <p><strong>A policy version is immutable and every assessment records the one that produced it.</strong>
 * Editing a policy creates a new version; the old rows stay exactly as they were. Without that, a
 * score from last month could not be reproduced — and a threat score that cannot be reproduced
 * cannot be defended to a reviewer, which section 6.3.4 requires it to be.
 *
 * <p>Validation is in the constructor, so an invalid policy cannot exist as an object. Weights must
 * cover every factor and sum to 1.0, which is what makes the composite score fall in {@code [0,1]}
 * by construction rather than by a clamp that would silently hide a misconfigured weight.
 *
 * @param policyId      stable identity across versions
 * @param version       the version label recorded on every assessment, e.g. {@code policy-v3}
 * @param effectiveAt   when this version came into force
 * @param weights       every factor's weight; must be complete and sum to 1.0
 * @param thresholds    the level boundaries
 * @param normalisation the curves raw measurements are normalised against
 * @param reliabilityFloor the minimum available weight for a score to count as reliable
 * @param rules         the recommendation rules, held in priority order
 */
public record ThreatPolicy(
        String policyId,
        String version,
        Instant effectiveAt,
        Map<ThreatFactorType, Double> weights,
        LevelThresholds thresholds,
        NormalisationParameters normalisation,
        double reliabilityFloor,
        List<RecommendationRule> rules) {

    private static final double WEIGHT_SUM_TOLERANCE = 1e-6;

    /** Section 6.3.4's normative weights, promoted from section 2.4.2's worked example at M7.A. */
    public static final Map<ThreatFactorType, Double> DEFAULT_WEIGHTS = Map.of(
            ThreatFactorType.MODEL_CONFIDENCE, 0.40,
            ThreatFactorType.PROXIMITY, 0.25,
            ThreatFactorType.CLOSING_VELOCITY, 0.20,
            ThreatFactorType.TRAJECTORY_INTENT, 0.10,
            ThreatFactorType.SENSOR_AGREEMENT, 0.05);

    /**
     * The default reliability floor: at least the model-confidence weight plus one other factor.
     * Below that, a score is one opinion wearing the clothes of a composite.
     */
    public static final double DEFAULT_RELIABILITY_FLOOR = 0.50;

    public ThreatPolicy {
        if (policyId == null || policyId.isBlank() || version == null || version.isBlank()) {
            throw new IllegalArgumentException("A policy needs an id and a version");
        }
        if (effectiveAt == null || thresholds == null || normalisation == null) {
            throw new IllegalArgumentException(
                    "A policy needs an effective time, thresholds and normalisation parameters");
        }
        if (reliabilityFloor < 0.0 || reliabilityFloor > 1.0) {
            throw new IllegalArgumentException(
                    "Reliability floor must be in [0,1], was " + reliabilityFloor);
        }
        weights = validatedWeights(weights);
        rules = List.copyOf(rules == null ? List.of() : rules).stream()
                .sorted(Comparator.comparingInt(RecommendationRule::priority))
                .toList();
        requireUniqueRuleIds(rules);
    }

    /**
     * The policy a deployment gets before anybody configures one: section 6.3.4's normative weights
     * and thresholds, with a rule set that reproduces section 2.4.2's rendered advice.
     *
     * <p>Shipping a working default rather than an empty policy is deliberate. An unconfigured
     * threat service that scored nothing would look identical to a working one with no threats in
     * the airspace, and the difference between those two matters more here than almost anywhere else
     * in the platform.
     *
     * @param effectiveAt when this version is deemed to have come into force
     * @return the default policy
     */
    public static ThreatPolicy defaultPolicy(Instant effectiveAt) {
        return new ThreatPolicy(
                "default", "policy-v1", effectiveAt,
                DEFAULT_WEIGHTS, LevelThresholds.DEFAULT, NormalisationParameters.DEFAULT,
                DEFAULT_RELIABILITY_FLOOR, defaultRules());
    }

    /**
     * The default recommendation ladder.
     *
     * <p>Two of these deserve their reasoning stated, because both were choices rather than
     * transcriptions:
     *
     * <ul>
     *   <li><b>{@code evidence-too-thin} sits at the top.</b> An unreliable score does not get a
     *       confident instruction attached to it at any threat level. Section 6.3.4 requires missing
     *       factors to be reported rather than substituted; advising firmly on a score built from
     *       one factor of five would undo that at the last step, where the operator actually reads
     *       it.
     *   <li><b>{@code critical-approach} advises {@code TRACK_AND_MONITOR}, not escalation.</b> That
     *       reproduces section 2.4.2, which renders "TRACK &amp; MONITOR — request visual ID" for a
     *       composite of 0.853, and section 2.4.3, which records escalation as the <em>operator's</em>
     *       decision rather than the system's advice. Escalation is reserved for
     *       {@code sustained-critical-approach}, where the approach has held long enough that intent
     *       is confirmed rather than momentary.
     * </ul>
     *
     * @return the default rules, in priority order
     */
    public static List<RecommendationRule> defaultRules() {
        return List.of(
                new RecommendationRule("evidence-too-thin", 10,
                        ThreatLevel.LOW, false, ReliabilityCondition.UNRELIABLE_ONLY, false, 0,
                        Recommendation.RECOMMEND_OPERATOR_REVIEW,
                        "Too few factors could be evaluated for the system to advise; a person "
                                + "should look at this track."),
                new RecommendationRule("sustained-critical-approach", 20,
                        ThreatLevel.CRITICAL, true, ReliabilityCondition.RELIABLE_ONLY, false, 4,
                        Recommendation.ESCALATE_TO_SUPERVISOR,
                        "A critical track has held its approach across consecutive updates."),
                new RecommendationRule("critical-approach", 30,
                        ThreatLevel.CRITICAL, true, ReliabilityCondition.ANY, false, 0,
                        Recommendation.TRACK_AND_MONITOR,
                        "Critical composite score on a track closing on a protected asset; "
                                + "request visual identification."),
                new RecommendationRule("unidentified-approach", 40,
                        ThreatLevel.MEDIUM, true, ReliabilityCondition.ANY, true, 0,
                        Recommendation.REQUEST_VISUAL_ID,
                        "No service has identified this track while it closes on a protected asset."),
                new RecommendationRule("elevated", 50,
                        ThreatLevel.HIGH, false, ReliabilityCondition.ANY, false, 0,
                        Recommendation.TRACK_AND_MONITOR,
                        "Elevated composite score."),
                new RecommendationRule("moderate", 60,
                        ThreatLevel.MEDIUM, false, ReliabilityCondition.ANY, false, 0,
                        Recommendation.TRACK_AND_MONITOR,
                        "Moderate composite score."),
                new RecommendationRule("routine", 70,
                        ThreatLevel.LOW, false, ReliabilityCondition.ANY, false, 0,
                        Recommendation.MONITOR,
                        "Routine observation."));
    }

    /**
     * @param type a factor
     * @return the weight this policy gives it; never absent, because the constructor rejects an
     *         incomplete weight map
     */
    public double weightOf(ThreatFactorType type) {
        return weights.get(type);
    }

    private static Map<ThreatFactorType, Double> validatedWeights(Map<ThreatFactorType, Double> weights) {
        if (weights == null) {
            throw new IllegalArgumentException("A policy needs a weight for every factor");
        }
        Map<ThreatFactorType, Double> copy = new EnumMap<>(ThreatFactorType.class);
        copy.putAll(weights);
        for (ThreatFactorType type : ThreatFactorType.values()) {
            Double weight = copy.get(type);
            if (weight == null) {
                throw new IllegalArgumentException("No weight configured for factor " + type
                        + ". A factor with no weight would be silently dropped from every score.");
            }
            if (weight < 0.0 || weight > 1.0) {
                throw new IllegalArgumentException(
                        "Weight for " + type + " must be in [0,1], was " + weight);
            }
        }
        double sum = copy.values().stream().mapToDouble(Double::doubleValue).sum();
        if (Math.abs(sum - 1.0) > WEIGHT_SUM_TOLERANCE) {
            throw new IllegalArgumentException(
                    "Factor weights must sum to 1.0 so the composite score is in [0,1] by"
                            + " construction; they summed to " + sum);
        }
        return java.util.Collections.unmodifiableMap(copy);
    }

    private static void requireUniqueRuleIds(List<RecommendationRule> rules) {
        long distinct = rules.stream().map(RecommendationRule::ruleId).distinct().count();
        if (distinct != rules.size()) {
            throw new IllegalArgumentException(
                    "Rule ids must be unique within a policy version — an assessment records the id"
                            + " of the rule that fired, and a duplicate makes that record ambiguous");
        }
    }
}
