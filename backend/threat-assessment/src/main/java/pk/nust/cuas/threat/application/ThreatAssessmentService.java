package pk.nust.cuas.threat.application;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pk.nust.cuas.threat.domain.model.InferenceObservation;
import pk.nust.cuas.threat.domain.model.ThreatAssessment;
import pk.nust.cuas.threat.domain.model.ThreatLevel;
import pk.nust.cuas.threat.domain.model.ThreatLevelTransition;
import pk.nust.cuas.threat.domain.model.TrackEvidence;
import pk.nust.cuas.threat.domain.model.TrackSnapshot;
import pk.nust.cuas.threat.domain.policy.ThreatPolicy;
import pk.nust.cuas.threat.domain.port.ThreatAssessmentRepository;
import pk.nust.cuas.threat.domain.port.ThreatEventPublisher;
import pk.nust.cuas.threat.domain.service.ThreatScorer;

/**
 * Scores a track whenever new evidence about it arrives (ARCHITECTURE.md section 4.1.7).
 *
 * <p>Two topics feed this service and neither is sufficient alone: {@code track.updated.v1} carries
 * kinematics and no verdict, {@code ai.inference.completed.v1} carries a verdict and no kinematics.
 * Both entry points do the same four things — fold the new fact into the track's accumulated
 * evidence, score it against the policy in force, persist, and publish — because the alternative,
 * a different pipeline per source, is how the two would drift into disagreeing about the same track.
 *
 * <p><strong>An inference for a track that has never been updated is still scored.</strong> The
 * assessment comes out with model confidence available and the four kinematic factors absent, which
 * makes it unreliable, which makes the rules engine advise operator review. That is the honest
 * outcome and the useful one: something has classified an object the pipeline has told us nothing
 * else about, and suppressing the assessment until a track update arrived would hide exactly that.
 */
@Service
public class ThreatAssessmentService {

    private static final Logger log = LoggerFactory.getLogger(ThreatAssessmentService.class);

    private final ThreatAssessmentRepository assessments;
    private final ActivePolicySupplier policies;
    private final ThreatScorer scorer;
    private final ThreatEventPublisher events;
    private final Clock clock;

    public ThreatAssessmentService(ThreatAssessmentRepository assessments,
                                   ActivePolicySupplier policies,
                                   ThreatScorer scorer,
                                   ThreatEventPublisher events,
                                   Clock clock) {
        this.assessments = assessments;
        this.policies = policies;
        this.scorer = scorer;
        this.events = events;
        this.clock = clock;
    }

    /**
     * Re-scores a track from a {@code track.updated.v1} event.
     *
     * @param snapshot the new track facts
     * @return the assessment produced, or empty when this was a redelivery of one already scored
     */
    @Transactional
    public Optional<ThreatAssessment> onTrackUpdate(TrackSnapshot snapshot) {
        return assess(snapshot.trackId(), evidence -> evidence.with(snapshot));
    }

    /**
     * Re-scores a track from an {@code ai.inference.completed.v1} event.
     *
     * @param trackId     the track the result is about
     * @param observation the service's verdict
     * @return the assessment produced, or empty when this was a redelivery of one already scored
     */
    @Transactional
    public Optional<ThreatAssessment> onInference(UUID trackId, InferenceObservation observation) {
        return assess(trackId, evidence -> evidence.with(observation));
    }

    private Optional<ThreatAssessment> assess(UUID trackId,
                                              java.util.function.UnaryOperator<TrackEvidence> fold) {
        ThreatPolicy policy = policies.active();
        Optional<ThreatAssessment> previous = assessments.findLatest(trackId);
        TrackEvidence evidence = fold.apply(assessments.evidenceFor(trackId));

        ThreatAssessment assessment = scorer.score(trackId, evidence, policy, Instant.now(clock));

        if (!assessments.save(assessment)) {
            // Same inputs, same policy, same answer: a redelivery. Publishing again would put a
            // duplicate on three topics, and alert-service would raise a duplicate alert from the
            // level transition — the failure section 5.7 singles out.
            log.debug("Skipping redelivered assessment for track {} (inputs hash {})",
                    trackId, assessment.inputsHash());
            return Optional.empty();
        }

        publish(assessment, previous);
        return Optional.of(assessment);
    }

    /**
     * Publication order is deliberate: the assessment first, then the advice, then the transition.
     * The transition is what alert-service acts on, so it is published last — a consumer that reacts
     * to it can rely on the assessment it cites already being on its topic.
     */
    private void publish(ThreatAssessment assessment, Optional<ThreatAssessment> previous) {
        events.publishAssessed(assessment);
        events.publishRecommendation(assessment);

        transition(assessment, previous).ifPresent(change -> {
            events.publishLevelChanged(change);
            log.info("Track {} threat level {} -> {} (score {} under {})",
                    assessment.trackId(),
                    change.previousLevel().map(Enum::name).orElse("none"),
                    change.currentLevel(), assessment.compositeScore(), assessment.policyVersion());
        });
    }

    /**
     * @return the transition, or empty when the level did not move — including when the track is
     *         still unassessed, and when it has <em>become</em> unassessed. A track whose evidence
     *         has expired has not de-escalated to LOW; it has stopped being judged, and announcing
     *         that as a level change would tell alert-service to close an alert on the strength of
     *         the system knowing less than it did before.
     */
    private Optional<ThreatLevelTransition> transition(ThreatAssessment assessment,
                                                       Optional<ThreatAssessment> previous) {
        Optional<ThreatLevel> current = assessment.level();
        if (current.isEmpty()) {
            return Optional.empty();
        }
        Optional<ThreatLevel> before = previous.flatMap(ThreatAssessment::level);
        if (before.filter(current.get()::equals).isPresent()) {
            return Optional.empty();
        }
        return Optional.of(new ThreatLevelTransition(
                assessment.trackId(),
                assessment.sectorId(),
                assessment.assessmentId(),
                before,
                current.get(),
                previous.map(ThreatAssessment::compositeScore).orElse(0.0),
                assessment.compositeScore(),
                assessment.recommendation(),
                assessment.assessedAt(),
                assessment.correlationId()));
    }
}
