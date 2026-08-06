package pk.nust.cuas.threat.domain.service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import pk.nust.cuas.commons.id.Uuidv7Generator;
import pk.nust.cuas.threat.domain.model.AdvisoryRecommendation;
import pk.nust.cuas.threat.domain.model.AssessmentConfidence;
import pk.nust.cuas.threat.domain.model.AssetApproach;
import pk.nust.cuas.threat.domain.model.ThreatAssessment;
import pk.nust.cuas.threat.domain.model.ThreatFactor;
import pk.nust.cuas.threat.domain.model.ThreatFactorType;
import pk.nust.cuas.threat.domain.model.ThreatLevel;
import pk.nust.cuas.threat.domain.model.TrackEvidence;
import pk.nust.cuas.threat.domain.model.TrackSnapshot;
import pk.nust.cuas.threat.domain.policy.NormalisationParameters;
import pk.nust.cuas.threat.domain.policy.RuleContext;
import pk.nust.cuas.threat.domain.policy.ThreatPolicy;

/**
 * The weighted rules engine (ARCHITECTURE.md section 6.3.4) — Track A code, not an AI service.
 *
 * <p>Section 6.3.4 describes {@code threat-scorer} as a rules engine plus a gradient-boosted
 * residual, and section 4.1.7 settles which half lives where: <strong>this</strong> is the
 * deterministic, explainable half, and it must produce a score whether or not any model is
 * reachable. The residual arrives as an ordinary inference result on
 * {@code ai.inference.completed.v1} and is weighed as one factor of five — never as a synchronous
 * call, and never as the authority. A threat score that vanished when the AI boundary degraded would
 * invert section 5.6's governing principle: a drone scored late is a degraded outcome, a drone never
 * scored is an undetected one.
 *
 * <p>Pure and framework-free. Given the same evidence and the same policy it returns the same
 * assessment, which is what makes a past score reproducible from the record — and reproducibility is
 * what makes it defensible to a reviewer.
 *
 * <p><strong>Every factor either has evidence or is declared absent.</strong> There is no branch here
 * that substitutes a default for a measurement the system does not have. That is section 6.3.4's
 * confidence requirement, and it is enforced by {@link ThreatFactor}'s own invariant rather than by
 * discipline.
 */
public class ThreatScorer {

    private final RecommendationEngine recommendations;
    private final Uuidv7Generator ids;

    /**
     * @param recommendations the rules engine that turns a level into advice
     * @param ids             UUIDv7 source; injected rather than called statically so a test can make
     *                        assessment ids deterministic and assert on a whole assessment
     */
    public ThreatScorer(RecommendationEngine recommendations, Uuidv7Generator ids) {
        this.recommendations = recommendations;
        this.ids = ids;
    }

    /**
     * Scores a track against a policy.
     *
     * @param trackId    the track
     * @param evidence   everything currently known about it
     * @param policy     the policy in force
     * @param assessedAt the platform clock
     * @return the assessment; its level is absent when no factor could be evaluated
     */
    public ThreatAssessment score(UUID trackId, TrackEvidence evidence, ThreatPolicy policy,
                                  Instant assessedAt) {
        List<ThreatFactor> factors = factors(evidence, policy);
        AssessmentConfidence confidence = AssessmentConfidence.of(factors, policy.reliabilityFloor());
        double composite = factors.stream().mapToDouble(ThreatFactor::contribution).sum();

        Optional<ThreatLevel> level = confidence.isEmpty()
                ? Optional.empty()
                : Optional.of(policy.thresholds().levelFor(composite));

        AdvisoryRecommendation recommendation = recommendations.advise(
                context(level, evidence, confidence), policy);

        TrackSnapshot snapshot = evidence.snapshot().orElse(null);
        return new ThreatAssessment(
                ids.generate(assessedAt),
                trackId,
                snapshot == null ? "" : snapshot.sectorId(),
                composite,
                level,
                factors,
                confidence,
                recommendation,
                policy.policyId(),
                policy.version(),
                policy.effectiveAt(),
                evidence,
                AssessmentInputsHash.of(evidence, policy),
                assessedAt,
                snapshot == null ? assessedAt : snapshot.observedAt(),
                snapshot == null ? "" : snapshot.correlationId());
    }

    private List<ThreatFactor> factors(TrackEvidence evidence, ThreatPolicy policy) {
        List<ThreatFactor> factors = new ArrayList<>(ThreatFactorType.values().length);
        factors.add(modelConfidence(evidence, policy));
        factors.add(proximity(evidence, policy));
        factors.add(closingVelocity(evidence, policy));
        factors.add(trajectoryIntent(evidence, policy));
        factors.add(sensorAgreement(evidence, policy));
        return List.copyOf(factors);
    }

    private ThreatFactor modelConfidence(TrackEvidence evidence, ThreatPolicy policy) {
        double weight = policy.weightOf(ThreatFactorType.MODEL_CONFIDENCE);
        return evidence.mostConfident()
                .map(o -> ThreatFactor.available(
                        ThreatFactorType.MODEL_CONFIDENCE, o.confidence(), weight))
                .orElseGet(() -> ThreatFactor.unavailable(
                        ThreatFactorType.MODEL_CONFIDENCE, weight,
                        "No AI service has produced a classification for this track."));
    }

    private ThreatFactor proximity(TrackEvidence evidence, ThreatPolicy policy) {
        double weight = policy.weightOf(ThreatFactorType.PROXIMITY);
        return approach(evidence)
                .map(cpa -> ThreatFactor.available(
                        ThreatFactorType.PROXIMITY,
                        policy.normalisation().normaliseProximity(cpa.distanceM()),
                        weight))
                .orElseGet(() -> ThreatFactor.unavailable(
                        ThreatFactorType.PROXIMITY, weight,
                        "No protected asset is within this track's prediction horizon."));
    }

    /**
     * Closing velocity is <em>available with a value of zero</em> for a receding track, not absent.
     * The system has the geometry and it says the track is moving away — that is evidence, and a
     * strong one. Absence is reserved for the case where there is no approach geometry at all.
     */
    private ThreatFactor closingVelocity(TrackEvidence evidence, ThreatPolicy policy) {
        double weight = policy.weightOf(ThreatFactorType.CLOSING_VELOCITY);
        Optional<AssetApproach> approach = approach(evidence);
        if (approach.isEmpty()) {
            return ThreatFactor.unavailable(ThreatFactorType.CLOSING_VELOCITY, weight,
                    "No protected asset is within this track's prediction horizon.");
        }
        double speedMs = evidence.snapshot().map(TrackSnapshot::speedMs).orElse(0.0);
        double closureRate = approach.get().closureRateMs(speedMs);
        return ThreatFactor.available(ThreatFactorType.CLOSING_VELOCITY,
                policy.normalisation().normaliseClosure(closureRate), weight);
    }

    /**
     * Intent is a claim about a path that <em>holds</em>, so a single observation cannot support it.
     * The factor stays absent until the track has been seen closing more than once — which is the
     * honest answer on a track's first update, and exactly the case section 6.3.4 wants reported
     * rather than defaulted.
     */
    private ThreatFactor trajectoryIntent(TrackEvidence evidence, ThreatPolicy policy) {
        double weight = policy.weightOf(ThreatFactorType.TRAJECTORY_INTENT);
        if (approach(evidence).isEmpty()) {
            return ThreatFactor.unavailable(ThreatFactorType.TRAJECTORY_INTENT, weight,
                    "No protected asset is within this track's prediction horizon.");
        }
        if (evidence.consecutiveClosingCount() < 2) {
            return ThreatFactor.unavailable(ThreatFactorType.TRAJECTORY_INTENT, weight,
                    "The track has not yet been observed closing across consecutive updates.");
        }
        NormalisationParameters curves = policy.normalisation();
        return ThreatFactor.available(ThreatFactorType.TRAJECTORY_INTENT,
                curves.normaliseIntent(evidence.consecutiveClosingCount()), weight);
    }

    private ThreatFactor sensorAgreement(TrackEvidence evidence, ThreatPolicy policy) {
        double weight = policy.weightOf(ThreatFactorType.SENSOR_AGREEMENT);
        return evidence.labelAgreement()
                .map(agreement -> ThreatFactor.available(
                        ThreatFactorType.SENSOR_AGREEMENT, agreement, weight))
                .orElseGet(() -> ThreatFactor.unavailable(
                        ThreatFactorType.SENSOR_AGREEMENT, weight,
                        "Fewer than two services have reported on this track; one cannot"
                                + " corroborate itself."));
    }

    private static Optional<AssetApproach> approach(TrackEvidence evidence) {
        return evidence.snapshot().flatMap(TrackSnapshot::approach);
    }

    private static RuleContext context(Optional<ThreatLevel> level, TrackEvidence evidence,
                                       AssessmentConfidence confidence) {
        // An unassessed track is still advised on — the rules see LOW so the evidence-too-thin rule
        // can fire. Publishing no recommendation at all would leave the operator with a track on
        // the screen and nothing said about it.
        return new RuleContext(
                level.orElse(ThreatLevel.LOW),
                evidence.snapshot().map(TrackSnapshot::isClosing).orElse(false),
                confidence.isReliable(),
                !evidence.observations().isEmpty(),
                evidence.consecutiveClosingCount());
    }
}
