package pk.nust.cuas.threat.domain.model;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Everything known about one track at the moment it is scored — the complete input to
 * {@link pk.nust.cuas.threat.domain.service.ThreatScorer}.
 *
 * <p>This service is fed by two independent topics that arrive on their own schedules: a track
 * update carries kinematics but no verdict, and an inference result carries a verdict but no
 * kinematics. Scoring needs both, so the evidence accumulates here and is <em>persisted with the
 * assessment it produced</em>. That placement is deliberate: an assessment must be reproducible from
 * the record (section 5.3.3), and storing its inputs beside its output is what makes replaying a
 * past score against a past policy possible. It also means a re-score triggered by either topic
 * recovers the other topic's contribution without a second cache to keep coherent.
 *
 * <p>Immutable. Folding in a new observation returns a new instance, so a scoring pass can never
 * mutate the evidence a concurrent pass is reading.
 *
 * @param snapshot                 the latest track facts, absent until the first track update
 * @param observationsByModel      the latest observation from each reporting service, in arrival
 *                                 order
 * @param consecutiveClosingCount  how many consecutive track updates have shown this track closing
 *                                 on an asset — the evidence behind
 *                                 {@link ThreatFactorType#TRAJECTORY_INTENT}
 */
public record TrackEvidence(
        Optional<TrackSnapshot> snapshot,
        Map<String, InferenceObservation> observationsByModel,
        int consecutiveClosingCount) {

    public TrackEvidence {
        if (snapshot == null || observationsByModel == null) {
            throw new IllegalArgumentException("Track evidence requires a snapshot and observations");
        }
        if (consecutiveClosingCount < 0) {
            throw new IllegalArgumentException(
                    "Consecutive closing count cannot be negative, was " + consecutiveClosingCount);
        }
        observationsByModel = Map.copyOf(observationsByModel);
    }

    /** @return evidence for a track nothing has yet reported on */
    public static TrackEvidence empty() {
        return new TrackEvidence(Optional.empty(), Map.of(), 0);
    }

    /**
     * Folds in a track update.
     *
     * <p>The closing streak advances only while the track keeps closing, and resets to zero the
     * moment it does not. Intent is a claim about a path that <em>holds</em>, so a track that closes,
     * turns away, and closes again has not demonstrated the same thing as one that never turned —
     * and a counter that merely paused would say it had.
     *
     * @param update the new track facts
     * @return the folded evidence
     */
    public TrackEvidence with(TrackSnapshot update) {
        int streak = update.isClosing() ? consecutiveClosingCount + 1 : 0;
        return new TrackEvidence(Optional.of(update), observationsByModel, streak);
    }

    /**
     * Folds in an inference result, replacing any earlier result from the same service.
     *
     * <p>Only the latest per service is kept. A service that reports repeatedly is not additional
     * corroboration — sensor agreement counts <em>independent</em> services, and letting one
     * talkative model contribute several votes would let it manufacture its own agreement.
     *
     * @param observation the result
     * @return the folded evidence
     */
    public TrackEvidence with(InferenceObservation observation) {
        Map<String, InferenceObservation> merged = new LinkedHashMap<>(observationsByModel);
        merged.put(observation.modelId(), observation);
        return new TrackEvidence(snapshot, merged, consecutiveClosingCount);
    }

    /** @return the latest observation from each reporting service */
    public Collection<InferenceObservation> observations() {
        return observationsByModel.values();
    }

    /**
     * The most confident verdict currently held.
     *
     * <p>Confidence is calibrated and comparable across services by construction (section 6.2's
     * {@code ConfidenceReport}), so taking the maximum is a meaningful ordering rather than an
     * arbitrary pick — the same reasoning the orchestrator applies when several services answer one
     * request.
     *
     * @return the observation, or empty when nothing has reported
     */
    public Optional<InferenceObservation> mostConfident() {
        return observations().stream().max(java.util.Comparator.comparingDouble(
                InferenceObservation::confidence));
    }

    /**
     * Cross-service agreement on the label.
     *
     * @return the fraction of reporting services that agree with the most confident one, or empty
     *         when fewer than two services have reported — one service cannot corroborate itself,
     *         and reporting 1.0 for a single voice would be the strongest possible agreement claim
     *         made on the weakest possible evidence
     */
    public Optional<Double> labelAgreement() {
        if (observationsByModel.size() < 2) {
            return Optional.empty();
        }
        String leading = mostConfident().orElseThrow().label();
        long agreeing = observations().stream()
                .filter(o -> o.label().equals(leading))
                .count();
        return Optional.of((double) agreeing / observationsByModel.size());
    }

    /** @return the reporting services, for the operator-facing breakdown */
    public List<String> reportingModels() {
        return List.copyOf(observationsByModel.keySet());
    }
}
