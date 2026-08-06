package pk.nust.cuas.threat.domain.policy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.within;

import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import pk.nust.cuas.threat.domain.model.Recommendation;
import pk.nust.cuas.threat.domain.model.ThreatFactorType;
import pk.nust.cuas.threat.domain.model.ThreatLevel;

/** Policy validation (ARCHITECTURE.md sections 4.1.7, 6.3.4). */
class ThreatPolicyTest {

    private static final Instant T = Instant.parse("2026-08-06T14:00:00Z");

    @Test
    void theDefaultsAreSection634sNormativeWeights() {
        ThreatPolicy policy = ThreatPolicy.defaultPolicy(T);

        assertThat(policy.weightOf(ThreatFactorType.MODEL_CONFIDENCE)).isEqualTo(0.40);
        assertThat(policy.weightOf(ThreatFactorType.PROXIMITY)).isEqualTo(0.25);
        assertThat(policy.weightOf(ThreatFactorType.CLOSING_VELOCITY)).isEqualTo(0.20);
        assertThat(policy.weightOf(ThreatFactorType.TRAJECTORY_INTENT)).isEqualTo(0.10);
        assertThat(policy.weightOf(ThreatFactorType.SENSOR_AGREEMENT)).isEqualTo(0.05);
    }

    @Test
    void weightsMustSumToOneSoTheCompositeIsBoundedByConstruction() {
        // Not by a clamp. A clamp would silently absorb a misconfigured weight and produce scores
        // that look valid for every track until somebody audited the arithmetic.
        assertThat(ThreatPolicy.defaultPolicy(T).weights().values().stream()
                .mapToDouble(Double::doubleValue).sum()).isCloseTo(1.0, within(1e-9));

        Map<ThreatFactorType, Double> tooMuch = new EnumMap<>(ThreatPolicy.DEFAULT_WEIGHTS);
        tooMuch.put(ThreatFactorType.PROXIMITY, 0.45);

        assertThatIllegalArgumentException()
                .isThrownBy(() -> policyWith(tooMuch))
                .withMessageContaining("sum to 1.0");
    }

    @Test
    void aFactorWithNoWeightIsRejectedRatherThanSilentlyDropped() {
        Map<ThreatFactorType, Double> incomplete = new EnumMap<>(ThreatFactorType.class);
        incomplete.put(ThreatFactorType.MODEL_CONFIDENCE, 0.60);
        incomplete.put(ThreatFactorType.PROXIMITY, 0.40);

        assertThatIllegalArgumentException()
                .isThrownBy(() -> policyWith(incomplete))
                .withMessageContaining("No weight configured");
    }

    @Test
    void rulesAreHeldInPriorityOrderRegardlessOfInsertionOrder() {
        List<RecommendationRule> shuffled = new ArrayList<>(ThreatPolicy.defaultRules());
        java.util.Collections.reverse(shuffled);

        ThreatPolicy policy = new ThreatPolicy("p", "v1", T, ThreatPolicy.DEFAULT_WEIGHTS,
                LevelThresholds.DEFAULT, NormalisationParameters.DEFAULT, 0.5, shuffled);

        assertThat(policy.rules())
                .isSortedAccordingTo(java.util.Comparator.comparingInt(RecommendationRule::priority));
    }

    @Test
    void duplicateRuleIdsAreRejected() {
        // Every assessment records the id of the rule that fired; a duplicate makes that record
        // ambiguous, which defeats the reason the id is stored at all.
        List<RecommendationRule> duplicated = List.of(
                rule("same", 10), rule("same", 20));

        assertThatIllegalArgumentException()
                .isThrownBy(() -> new ThreatPolicy("p", "v1", T, ThreatPolicy.DEFAULT_WEIGHTS,
                        LevelThresholds.DEFAULT, NormalisationParameters.DEFAULT, 0.5, duplicated))
                .withMessageContaining("unique");
    }

    @Test
    void aPolicyMustCarryAVersionSoItsScoresCanBeReproduced() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new ThreatPolicy("p", "  ", T, ThreatPolicy.DEFAULT_WEIGHTS,
                        LevelThresholds.DEFAULT, NormalisationParameters.DEFAULT, 0.5, List.of()));
    }

    @Test
    void theDefaultRuleSetCoversEveryLevel() {
        // A policy shipped with a gap would fall back to operator review for a whole band of scores,
        // which is safe but useless.
        ThreatPolicy policy = ThreatPolicy.defaultPolicy(T);
        for (ThreatLevel level : ThreatLevel.values()) {
            assertThat(policy.rules())
                    .as("no default rule matches a reliable, classified, non-closing %s track", level)
                    .anyMatch(r -> r.matches(new RuleContext(level, false, true, true, 0)));
        }
    }

    private static ThreatPolicy policyWith(Map<ThreatFactorType, Double> weights) {
        return new ThreatPolicy("p", "v1", T, weights, LevelThresholds.DEFAULT,
                NormalisationParameters.DEFAULT, 0.5, ThreatPolicy.defaultRules());
    }

    private static RecommendationRule rule(String id, int priority) {
        return new RecommendationRule(id, priority, ThreatLevel.LOW, false,
                ReliabilityCondition.ANY, false, 0, Recommendation.MONITOR, "");
    }
}
