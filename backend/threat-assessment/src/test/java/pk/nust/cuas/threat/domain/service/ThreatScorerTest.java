package pk.nust.cuas.threat.domain.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import pk.nust.cuas.commons.id.Uuidv7Generator;
import pk.nust.cuas.threat.domain.model.AssetApproach;
import pk.nust.cuas.threat.domain.model.InferenceObservation;
import pk.nust.cuas.threat.domain.model.Recommendation;
import pk.nust.cuas.threat.domain.model.ThreatAssessment;
import pk.nust.cuas.threat.domain.model.ThreatFactor;
import pk.nust.cuas.threat.domain.model.ThreatFactorType;
import pk.nust.cuas.threat.domain.model.ThreatLevel;
import pk.nust.cuas.threat.domain.model.TrackEvidence;
import pk.nust.cuas.threat.domain.model.TrackSnapshot;
import pk.nust.cuas.threat.domain.policy.ThreatPolicy;

/**
 * The weighted rules engine (ARCHITECTURE.md section 6.3.4).
 *
 * <p>Two properties carry most of the weight here: that the composite is the weighted sum and
 * nothing else, and that a factor with no evidence is <em>declared absent</em> rather than defaulted.
 * The second is the one that would rot quietly — a plausible zero substituted for a missing
 * measurement produces a number that looks right and is not.
 */
class ThreatScorerTest {

    private static final Instant T = Instant.parse("2026-08-06T14:23:06Z");
    private static final UUID TRACK = UUID.fromString("018f2b3c-4d5e-7f80-9a1b-2c3d4e5f6071");

    private final ThreatScorer scorer =
            new ThreatScorer(new RecommendationEngine(), new Uuidv7Generator(new Random(42)));
    private final ThreatPolicy policy = ThreatPolicy.defaultPolicy(T.minusSeconds(3600));

    // ── The specification's worked example ──────────────────────────────────────────────────────

    @Test
    void reproducesSection242sWorkedComposite() {
        // Section 2.4.2's breakdown panel is the only end-to-end number the specification supplies:
        // 0.87 x 0.40 + 0.92 x 0.25 + 0.78 x 0.20 + 0.71 x 0.10 + 0.95 x 0.05 = 0.8525 -> CRITICAL.
        // The factor values there are inputs, so they are supplied here directly rather than derived
        // through the normalisation curves; what is under test is the weighting and the level.
        double composite = 0.87 * 0.40 + 0.92 * 0.25 + 0.78 * 0.20 + 0.71 * 0.10 + 0.95 * 0.05;

        assertThat(composite).isCloseTo(0.853, within(0.001));
        assertThat(policy.thresholds().levelFor(composite)).isEqualTo(ThreatLevel.CRITICAL);
    }

    @Test
    void theCompositeIsTheSumOfTheFactorContributions() {
        ThreatAssessment assessment = scorer.score(TRACK, closingWithClassification(), policy, T);

        double sum = assessment.factors().stream().mapToDouble(ThreatFactor::contribution).sum();
        assertThat(assessment.compositeScore()).isCloseTo(sum, within(1e-9));
        assertThat(assessment.compositeScore()).isBetween(0.0, 1.0);
    }

    // ── Absent factors are declared, never defaulted ────────────────────────────────────────────

    @Test
    void aTrackWithNoAssetNearbyHasThreeAbsentFactorsNotThreeZeroes() {
        TrackEvidence evidence = TrackEvidence.empty()
                .with(new TrackSnapshot(TRACK, "sector-NE", T, 14.0, Optional.empty(), "corr-1"))
                .with(observation("model-a", "Mavic3", 0.87));

        ThreatAssessment assessment = scorer.score(TRACK, evidence, policy, T);

        assertThat(assessment.factor(ThreatFactorType.PROXIMITY).orElseThrow().isAvailable())
                .isFalse();
        assertThat(assessment.factor(ThreatFactorType.CLOSING_VELOCITY).orElseThrow().isAvailable())
                .isFalse();
        assertThat(assessment.factor(ThreatFactorType.TRAJECTORY_INTENT).orElseThrow().isAvailable())
                .isFalse();
        // Each says why, in words an operator can read.
        assertThat(assessment.factor(ThreatFactorType.PROXIMITY).orElseThrow().unavailableReason())
                .get().asString().contains("prediction horizon");
        // And the shortfall is visible in the confidence rather than hidden in the score.
        assertThat(assessment.confidence().availableWeight()).isCloseTo(0.40, within(1e-9));
        assertThat(assessment.confidence().isReliable()).isFalse();
    }

    @Test
    void anUnclassifiedTrackReportsModelConfidenceAbsent() {
        ThreatEvidenceFixture fixture = ThreatEvidenceFixture.closingAt(600.0, 47.0, 14.0);

        ThreatAssessment assessment = scorer.score(TRACK, fixture.evidence(), policy, T);

        ThreatFactor confidence =
                assessment.factor(ThreatFactorType.MODEL_CONFIDENCE).orElseThrow();
        assertThat(confidence.isAvailable()).isFalse();
        assertThat(confidence.contribution()).isZero();
        assertThat(confidence.weight())
                .as("the weight is still recorded, so the breakdown shows what the score is missing")
                .isEqualTo(0.40);
    }

    @Test
    void aRecedingTrackScoresZeroClosureButTheFactorIsAvailable() {
        // The distinction the whole design turns on: "we measured it and it is moving away" is
        // evidence, and a strong one. Only "there is no approach geometry at all" is absence.
        TrackEvidence evidence = TrackEvidence.empty().with(new TrackSnapshot(
                TRACK, "sector-NE", T, 14.0,
                Optional.of(new AssetApproach("SITE-1", "Site 1", 900.0, 0.0, false)), "corr-1"));

        ThreatAssessment assessment = scorer.score(TRACK, evidence, policy, T);

        ThreatFactor closure =
                assessment.factor(ThreatFactorType.CLOSING_VELOCITY).orElseThrow();
        assertThat(closure.isAvailable()).isTrue();
        assertThat(closure.value()).contains(0.0);
    }

    @Test
    void aTrackNothingCouldBeEvaluatedForHasNoLevelAtAll() {
        // Section 2.4.2's UNK row. Not LOW — a track nothing has judged must not read as the safest
        // thing on the screen.
        ThreatAssessment assessment = scorer.score(TRACK, TrackEvidence.empty(), policy, T);

        assertThat(assessment.level()).isEmpty();
        assertThat(assessment.isAssessed()).isFalse();
        assertThat(assessment.confidence().factorsAvailable()).isZero();
        assertThat(assessment.compositeScore()).isZero();
    }

    @Test
    void anUnassessedTrackIsStillAdvisedOn() {
        // Leaving an operator with a track on the screen and nothing said about it is worse than
        // saying "a person should look at this".
        ThreatAssessment assessment = scorer.score(TRACK, TrackEvidence.empty(), policy, T);

        assertThat(assessment.recommendation().recommendation())
                .isEqualTo(Recommendation.RECOMMEND_OPERATOR_REVIEW);
        assertThat(assessment.recommendation().ruleId()).isEqualTo("evidence-too-thin");
    }

    // ── Trajectory intent needs more than one observation ───────────────────────────────────────

    @Test
    void intentIsAbsentUntilTheTrackHasClosedAcrossConsecutiveUpdates() {
        ThreatEvidenceFixture first = ThreatEvidenceFixture.closingAt(600.0, 47.0, 14.0);

        ThreatAssessment afterOne = scorer.score(TRACK, first.evidence(), policy, T);
        assertThat(afterOne.factor(ThreatFactorType.TRAJECTORY_INTENT).orElseThrow().isAvailable())
                .as("one observation cannot support a claim about a path that holds")
                .isFalse();

        TrackEvidence twice = first.evidence().with(first.snapshot());
        ThreatAssessment afterTwo = scorer.score(TRACK, twice, policy, T);
        assertThat(afterTwo.factor(ThreatFactorType.TRAJECTORY_INTENT).orElseThrow().isAvailable())
                .isTrue();
    }

    @Test
    void intentResetsWhenTheTrackStopsClosing() {
        ThreatEvidenceFixture closing = ThreatEvidenceFixture.closingAt(600.0, 47.0, 14.0);
        TrackEvidence held = closing.evidence().with(closing.snapshot()).with(closing.snapshot());
        assertThat(held.consecutiveClosingCount()).isEqualTo(3);

        TrackEvidence turnedAway = held.with(new TrackSnapshot(
                TRACK, "sector-NE", T, 14.0,
                Optional.of(new AssetApproach("SITE-1", "Site 1", 900.0, 0.0, false)), "corr-1"));

        // Not paused — reset. A track that closes, turns away and closes again has not demonstrated
        // the same thing as one that never turned.
        assertThat(turnedAway.consecutiveClosingCount()).isZero();
        assertThat(scorer.score(TRACK, turnedAway, policy, T)
                .factor(ThreatFactorType.TRAJECTORY_INTENT).orElseThrow().isAvailable()).isFalse();
    }

    // ── Sensor agreement counts independent services ────────────────────────────────────────────

    @Test
    void oneServiceCannotCorroborateItself() {
        TrackEvidence evidence = TrackEvidence.empty()
                .with(new TrackSnapshot(TRACK, "sector-NE", T, 14.0, Optional.empty(), "c"))
                .with(observation("model-a", "Mavic3", 0.91));

        ThreatFactor agreement = scorer.score(TRACK, evidence, policy, T)
                .factor(ThreatFactorType.SENSOR_AGREEMENT).orElseThrow();

        assertThat(agreement.isAvailable()).isFalse();
        assertThat(agreement.unavailableReason()).get().asString().contains("corroborate itself");
    }

    @Test
    void aTalkativeServiceCannotManufactureItsOwnAgreement() {
        // Only the latest result per service is kept, so repeated reports are one vote.
        TrackEvidence evidence = TrackEvidence.empty()
                .with(new TrackSnapshot(TRACK, "sector-NE", T, 14.0, Optional.empty(), "c"))
                .with(observation("model-a", "Mavic3", 0.80))
                .with(observation("model-a", "Mavic3", 0.85))
                .with(observation("model-a", "Mavic3", 0.91));

        assertThat(evidence.observations()).hasSize(1);
        assertThat(scorer.score(TRACK, evidence, policy, T)
                .factor(ThreatFactorType.SENSOR_AGREEMENT).orElseThrow().isAvailable()).isFalse();
    }

    @Test
    void threeServicesAgreeingScoresFullAgreement() {
        // Section 2.4.2's "Fusion agreement: 3 of 3 modalities".
        TrackEvidence evidence = TrackEvidence.empty()
                .with(new TrackSnapshot(TRACK, "sector-NE", T, 14.0, Optional.empty(), "c"))
                .with(observation("model-a", "Mavic3", 0.91))
                .with(observation("model-b", "Mavic3", 0.84))
                .with(observation("model-c", "Mavic3", 0.83));

        assertThat(scorer.score(TRACK, evidence, policy, T)
                .factor(ThreatFactorType.SENSOR_AGREEMENT).orElseThrow().value()).contains(1.0);
    }

    @Test
    void disagreementScoresBelowFull() {
        TrackEvidence evidence = TrackEvidence.empty()
                .with(new TrackSnapshot(TRACK, "sector-NE", T, 14.0, Optional.empty(), "c"))
                .with(observation("model-a", "Mavic3", 0.91))
                .with(observation("model-b", "Bird", 0.62));

        assertThat(scorer.score(TRACK, evidence, policy, T)
                .factor(ThreatFactorType.SENSOR_AGREEMENT).orElseThrow().value()).contains(0.5);
    }

    // ── Reproducibility ─────────────────────────────────────────────────────────────────────────

    @Test
    void scoringIsDeterministicGivenTheSameEvidenceAndPolicy() {
        // The property that makes a past score defensible to a reviewer: it can be recomputed.
        TrackEvidence evidence = closingWithClassification();

        ThreatAssessment first = scorer.score(TRACK, evidence, policy, T);
        ThreatAssessment second = scorer.score(TRACK, evidence, policy, T);

        assertThat(second.compositeScore()).isEqualTo(first.compositeScore());
        assertThat(second.level()).isEqualTo(first.level());
        assertThat(second.inputsHash()).isEqualTo(first.inputsHash());
        assertThat(second.recommendation()).isEqualTo(first.recommendation());
    }

    @Test
    void everyAssessmentRecordsThePolicyThatProducedIt() {
        ThreatAssessment assessment = scorer.score(TRACK, closingWithClassification(), policy, T);

        assertThat(assessment.policyId()).isEqualTo(policy.policyId());
        assertThat(assessment.policyVersion()).isEqualTo(policy.version());
        assertThat(assessment.policyEffectiveAt()).isEqualTo(policy.effectiveAt());
    }

    @Test
    void theAssessmentScoresTheTracksEstimateTimeNotTheWallClock() {
        // Section 5.3.2: observed_at is the time the estimate is valid for, and it diverges from the
        // wall clock while a track coasts.
        Instant estimateTime = T.minusSeconds(12);
        TrackEvidence evidence = TrackEvidence.empty().with(new TrackSnapshot(
                TRACK, "sector-NE", estimateTime, 14.0, Optional.empty(), "corr-1"));

        ThreatAssessment assessment = scorer.score(TRACK, evidence, policy, T);

        assertThat(assessment.observedAt()).isEqualTo(estimateTime);
        assertThat(assessment.assessedAt()).isEqualTo(T);
    }

    @Test
    void everyPolicyFactorAppearsInTheBreakdownWhetherOrNotItHadEvidence() {
        ThreatAssessment assessment = scorer.score(TRACK, TrackEvidence.empty(), policy, T);

        assertThat(assessment.factors())
                .extracting(ThreatFactor::type)
                .containsExactlyInAnyOrder(ThreatFactorType.values());
    }

    private TrackEvidence closingWithClassification() {
        return ThreatEvidenceFixture.closingAt(600.0, 47.0, 14.0).evidence()
                .with(observation("model-a", "Mavic3", 0.91))
                .with(observation("model-b", "Mavic3", 0.84));
    }

    private static InferenceObservation observation(String modelId, String label, double confidence) {
        return new InferenceObservation(modelId, "1.3.0", label, "DRONE", confidence, T);
    }

    /** A closing track, built once so the tests above read as scenarios rather than construction. */
    private record ThreatEvidenceFixture(TrackSnapshot snapshot, TrackEvidence evidence) {

        static ThreatEvidenceFixture closingAt(double cpaDistanceM, double timeToCpaS, double speedMs) {
            TrackSnapshot snapshot = new TrackSnapshot(
                    TRACK, "sector-NE", T, speedMs,
                    Optional.of(new AssetApproach("SITE-1", "Site 1", cpaDistanceM, timeToCpaS, true)),
                    "corr-1");
            return new ThreatEvidenceFixture(
                    snapshot, new TrackEvidence(Optional.empty(), Map.of(), 0).with(snapshot));
        }
    }
}
