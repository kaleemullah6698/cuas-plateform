package pk.nust.cuas.threat.domain.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import pk.nust.cuas.threat.domain.model.AssetApproach;
import pk.nust.cuas.threat.domain.model.InferenceObservation;
import pk.nust.cuas.threat.domain.model.TrackEvidence;
import pk.nust.cuas.threat.domain.model.TrackSnapshot;
import pk.nust.cuas.threat.domain.policy.LevelThresholds;
import pk.nust.cuas.threat.domain.policy.NormalisationParameters;
import pk.nust.cuas.threat.domain.policy.ThreatPolicy;

/**
 * The idempotency key for at-least-once redelivery (ARCHITECTURE.md section 5.7).
 *
 * <p>The hash has to be stable in one direction and sensitive in the other, and both failures are
 * costly: too stable and a genuinely new inference is discarded as a duplicate, too sensitive and
 * every redelivery produces a duplicate assessment, a duplicate level transition, and the duplicate
 * alert section 5.7 names as the fastest way to destroy operator trust.
 */
class AssessmentInputsHashTest {

    private static final Instant T = Instant.parse("2026-08-06T14:23:06Z");
    private static final UUID TRACK = UUID.fromString("018f2b3c-4d5e-7f80-9a1b-2c3d4e5f6071");

    private final ThreatPolicy policy = ThreatPolicy.defaultPolicy(T.minusSeconds(3600));

    @Test
    void identicalEvidenceHashesIdentically() {
        assertThat(AssessmentInputsHash.of(evidence(), policy))
                .isEqualTo(AssessmentInputsHash.of(evidence(), policy));
    }

    @Test
    void aRedeliveryOfTheSameRecordIsRecognised() {
        // The same track update processed twice folds to the same evidence, so the same hash.
        TrackEvidence once = TrackEvidence.empty().with(snapshot(640.0));
        TrackEvidence twiceFromEmpty = TrackEvidence.empty().with(snapshot(640.0));

        assertThat(AssessmentInputsHash.of(once, policy))
                .isEqualTo(AssessmentInputsHash.of(twiceFromEmpty, policy));
    }

    @Test
    void aNewInferenceForTheSameTrackEstimateIsADifferentQuestion() {
        // The reason the key is the content rather than (trackId, observedAt): an inference arriving
        // for an unchanged track estimate must produce a new assessment, and shares that key exactly.
        TrackEvidence kinematicsOnly = TrackEvidence.empty().with(snapshot(640.0));
        TrackEvidence withVerdict = kinematicsOnly.with(observation("model-a", "Mavic3", 0.91));

        assertThat(AssessmentInputsHash.of(withVerdict, policy))
                .isNotEqualTo(AssessmentInputsHash.of(kinematicsOnly, policy));
    }

    @Test
    void aMovedTrackHashesDifferently() {
        assertThat(AssessmentInputsHash.of(TrackEvidence.empty().with(snapshot(640.0)), policy))
                .isNotEqualTo(
                        AssessmentInputsHash.of(TrackEvidence.empty().with(snapshot(300.0)), policy));
    }

    @Test
    void theSameEvidenceUnderARetunedPolicyIsADifferentClaim() {
        ThreatPolicy retuned = new ThreatPolicy(
                policy.policyId(), "policy-v2", policy.effectiveAt(), ThreatPolicy.DEFAULT_WEIGHTS,
                LevelThresholds.DEFAULT, NormalisationParameters.DEFAULT, 0.5,
                ThreatPolicy.defaultRules());

        assertThat(AssessmentInputsHash.of(evidence(), retuned))
                .isNotEqualTo(AssessmentInputsHash.of(evidence(), policy));
    }

    @Test
    void arrivalOrderOfInferenceResultsDoesNotChangeTheHash() {
        // Two consumers can fold the same two results in either order; the evidence is the same
        // either way, so the answer is, so the hash must be.
        TrackEvidence base = TrackEvidence.empty().with(snapshot(640.0));
        TrackEvidence ab = base.with(observation("model-a", "Mavic3", 0.91))
                .with(observation("model-b", "Mavic3", 0.84));
        TrackEvidence ba = base.with(observation("model-b", "Mavic3", 0.84))
                .with(observation("model-a", "Mavic3", 0.91));

        assertThat(AssessmentInputsHash.of(ab, policy))
                .isEqualTo(AssessmentInputsHash.of(ba, policy));
    }

    @Test
    void theClosingStreakIsPartOfTheKey() {
        // Intent is a scoring factor, so two otherwise identical observations at different points in
        // a closing streak are different questions.
        TrackEvidence once = TrackEvidence.empty().with(snapshot(640.0));
        TrackEvidence twice = once.with(snapshot(640.0));

        assertThat(AssessmentInputsHash.of(twice, policy))
                .isNotEqualTo(AssessmentInputsHash.of(once, policy));
    }

    @Test
    void concatenationCannotCollide() {
        // Fields are length-prefixed, so ("ab","c") and ("a","bc") cannot digest alike. Asserted
        // through two sector ids that would concatenate identically with the correlation id.
        TrackEvidence first = TrackEvidence.empty().with(new TrackSnapshot(
                TRACK, "ab", T, 14.0, Optional.empty(), "c"));
        TrackEvidence second = TrackEvidence.empty().with(new TrackSnapshot(
                TRACK, "a", T, 14.0, Optional.empty(), "bc"));

        assertThat(AssessmentInputsHash.of(first, policy))
                .isNotEqualTo(AssessmentInputsHash.of(second, policy));
    }

    @Test
    void emptyEvidenceStillHashes() {
        assertThat(AssessmentInputsHash.of(TrackEvidence.empty(), policy))
                .isNotBlank()
                .hasSize(64);
    }

    private TrackEvidence evidence() {
        return TrackEvidence.empty()
                .with(snapshot(640.0))
                .with(observation("model-a", "Mavic3", 0.91));
    }

    private static TrackSnapshot snapshot(double cpaDistanceM) {
        return new TrackSnapshot(TRACK, "sector-NE", T, 14.0,
                Optional.of(new AssetApproach("SITE-1", "Site 1", cpaDistanceM, 47.0, true)),
                "corr-1");
    }

    private static InferenceObservation observation(String modelId, String label, double confidence) {
        return new InferenceObservation(modelId, "1.3.0", label, "DRONE", confidence, T);
    }
}
