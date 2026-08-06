package pk.nust.cuas.threat.domain.port;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import pk.nust.cuas.threat.domain.model.ThreatAssessment;
import pk.nust.cuas.threat.domain.model.TrackEvidence;

/**
 * Persistence for assessments and the current score per track (ARCHITECTURE.md section 4.1.7,
 * {@code threatdb}). Declared in the domain as a port; the JPA implementation is an outward adapter.
 *
 * <p>Two shapes of the same data, deliberately: {@code assessments} is the immutable history — every
 * score ever produced, with the evidence and policy version that produced it — and
 * {@code threat_scores} is the current level per track, which is what the read API answers from and
 * what a level transition is measured against. Deriving the current level from the history on every
 * event would mean an ordering query per track update on the pipeline's critical path.
 */
public interface ThreatAssessmentRepository {

    /**
     * Persists an assessment and advances the track's current score in one unit of work.
     *
     * @param assessment the assessment
     * @return {@code true} when it was stored, {@code false} when an assessment with the same inputs
     *         hash already exists for this track — a redelivery, already answered. Reported rather
     *         than thrown: at-least-once delivery makes a duplicate an expected event, not a fault,
     *         and a caller that has to catch an exception for the normal case will eventually catch
     *         it too broadly.
     */
    boolean save(ThreatAssessment assessment);

    /**
     * @param trackId the track
     * @return its most recent assessment, or empty when it has never been scored
     */
    Optional<ThreatAssessment> findLatest(UUID trackId);

    /**
     * The evidence accumulated for a track, recovered from its latest assessment.
     *
     * <p>The two feeding topics arrive independently, so a scoring pass triggered by one must
     * recover the other's contribution. It comes from the last assessment rather than a separate
     * cache because the assessment already has to store its inputs to be reproducible — a second
     * store would be a second thing to keep coherent for no additional fact.
     *
     * @param trackId the track
     * @return the accumulated evidence, or {@link TrackEvidence#empty()} when the track is new
     */
    TrackEvidence evidenceFor(UUID trackId);

    /**
     * @param trackId the track
     * @param limit   the maximum number of assessments to return
     * @return the track's assessments, newest first
     */
    List<ThreatAssessment> history(UUID trackId, int limit);
}
