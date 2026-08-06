package pk.nust.cuas.threat.domain.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import pk.nust.cuas.threat.domain.model.AdvisoryRecommendation;
import pk.nust.cuas.threat.domain.model.Recommendation;
import pk.nust.cuas.threat.domain.model.ThreatLevel;
import pk.nust.cuas.threat.domain.policy.RecommendationRule;
import pk.nust.cuas.threat.domain.policy.ReliabilityCondition;
import pk.nust.cuas.threat.domain.policy.RuleContext;
import pk.nust.cuas.threat.domain.policy.ThreatPolicy;

/** The deterministic recommendation engine (ARCHITECTURE.md section 6.3.4). */
class RecommendationEngineTest {

    private static final Instant T = Instant.parse("2026-08-06T14:23:06Z");

    private final RecommendationEngine engine = new RecommendationEngine();
    private final ThreatPolicy policy = ThreatPolicy.defaultPolicy(T);

    @Test
    void anUnreliableScoreNeverGetsAConfidentInstruction() {
        // The top-priority rule, and the one that matters most: section 6.3.4 requires missing
        // factors to be reported rather than substituted, and advising firmly on a score built from
        // one factor of five would undo that at the last step, where the operator reads it.
        AdvisoryRecommendation advice = engine.advise(
                new RuleContext(ThreatLevel.CRITICAL, true, false, true, 9), policy);

        assertThat(advice.recommendation()).isEqualTo(Recommendation.RECOMMEND_OPERATOR_REVIEW);
        assertThat(advice.ruleId()).isEqualTo("evidence-too-thin");
    }

    @Test
    void aCriticalApproachAdvisesTrackingAndVisualIdentification() {
        // Reproduces section 2.4.2's rendered "TRACK & MONITOR - request visual ID" for a composite
        // of 0.853, and section 2.4.3, which records escalation as the operator's decision rather
        // than the system's advice.
        AdvisoryRecommendation advice = engine.advise(
                new RuleContext(ThreatLevel.CRITICAL, true, true, true, 1), policy);

        assertThat(advice.recommendation()).isEqualTo(Recommendation.TRACK_AND_MONITOR);
        assertThat(advice.ruleId()).isEqualTo("critical-approach");
        assertThat(advice.rationale()).contains("visual identification");
    }

    @Test
    void aSustainedCriticalApproachEscalates() {
        // Escalation is reserved for an approach that has held long enough for intent to be
        // confirmed rather than momentary.
        AdvisoryRecommendation advice = engine.advise(
                new RuleContext(ThreatLevel.CRITICAL, true, true, true, 4), policy);

        assertThat(advice.recommendation()).isEqualTo(Recommendation.ESCALATE_TO_SUPERVISOR);
        assertThat(advice.ruleId()).isEqualTo("sustained-critical-approach");
    }

    @Test
    void anUnidentifiedApproachAsksForEyesOnIt() {
        AdvisoryRecommendation advice = engine.advise(
                new RuleContext(ThreatLevel.MEDIUM, true, true, false, 2), policy);

        assertThat(advice.recommendation()).isEqualTo(Recommendation.REQUEST_VISUAL_ID);
        assertThat(advice.ruleId()).isEqualTo("unidentified-approach");
    }

    @Test
    void anIdentifiedModerateTrackIsSimplyTracked() {
        AdvisoryRecommendation advice = engine.advise(
                new RuleContext(ThreatLevel.MEDIUM, true, true, true, 2), policy);

        assertThat(advice.recommendation()).isEqualTo(Recommendation.TRACK_AND_MONITOR);
    }

    @Test
    void aLowTrackIsMonitored() {
        AdvisoryRecommendation advice = engine.advise(
                new RuleContext(ThreatLevel.LOW, false, true, true, 0), policy);

        assertThat(advice.recommendation()).isEqualTo(Recommendation.MONITOR);
        assertThat(advice.ruleId()).isEqualTo("routine");
    }

    @Test
    void firstMatchWinsAndPriorityDecidesWhichIsFirst() {
        ThreatPolicy reordered = new ThreatPolicy(
                "test", "v1", T, ThreatPolicy.DEFAULT_WEIGHTS,
                pk.nust.cuas.threat.domain.policy.LevelThresholds.DEFAULT,
                pk.nust.cuas.threat.domain.policy.NormalisationParameters.DEFAULT,
                0.5,
                // Deliberately supplied out of order: the policy sorts them, so precedence is a
                // property of the data rather than of the order rows came back in.
                List.of(
                        rule("second", 20, Recommendation.MONITOR),
                        rule("first", 10, Recommendation.ESCALATE_TO_SUPERVISOR)));

        AdvisoryRecommendation advice = engine.advise(
                new RuleContext(ThreatLevel.HIGH, false, true, true, 0), reordered);

        assertThat(advice.ruleId()).isEqualTo("first");
    }

    @Test
    void aPolicyGapFallsBackToOperatorReviewNotToMonitor() {
        // Falling back to MONITOR would be the quieter choice and the wrong one: it would report a
        // policy gap as an all-clear.
        ThreatPolicy gapped = new ThreatPolicy(
                "test", "v1", T, ThreatPolicy.DEFAULT_WEIGHTS,
                pk.nust.cuas.threat.domain.policy.LevelThresholds.DEFAULT,
                pk.nust.cuas.threat.domain.policy.NormalisationParameters.DEFAULT,
                0.5,
                List.of(new RecommendationRule("critical-only", 10, ThreatLevel.CRITICAL, false,
                        ReliabilityCondition.ANY, false, 0, Recommendation.MONITOR, "")));

        AdvisoryRecommendation advice = engine.advise(
                new RuleContext(ThreatLevel.LOW, false, true, true, 0), gapped);

        assertThat(advice.recommendation()).isEqualTo(Recommendation.RECOMMEND_OPERATOR_REVIEW);
        assertThat(advice.ruleId()).isEqualTo(RecommendationEngine.FALLBACK_RULE_ID);
    }

    @Test
    void thereIsAlwaysARecommendation() {
        // A track on the operator's screen with nothing said about it is indistinguishable from one
        // the system has judged harmless.
        for (ThreatLevel level : ThreatLevel.values()) {
            for (boolean closing : new boolean[]{true, false}) {
                for (boolean reliable : new boolean[]{true, false}) {
                    for (boolean classified : new boolean[]{true, false}) {
                        assertThat(engine.advise(
                                new RuleContext(level, closing, reliable, classified, 0), policy))
                                .as("level=%s closing=%s reliable=%s classified=%s",
                                        level, closing, reliable, classified)
                                .isNotNull();
                    }
                }
            }
        }
    }

    @Test
    void noDefaultRuleCanProduceAnythingOutsideTheClosedVocabulary() {
        assertThat(ThreatPolicy.defaultRules())
                .extracting(RecommendationRule::recommendation)
                .allSatisfy(r -> assertThat(Recommendation.values()).contains(r));
    }

    private static RecommendationRule rule(String id, int priority, Recommendation recommendation) {
        return new RecommendationRule(id, priority, ThreatLevel.LOW, false,
                ReliabilityCondition.ANY, false, 0, recommendation, "");
    }
}
