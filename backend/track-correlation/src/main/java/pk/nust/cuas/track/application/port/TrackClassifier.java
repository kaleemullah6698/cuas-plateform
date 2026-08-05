package pk.nust.cuas.track.application.port;

import java.util.Optional;
import pk.nust.cuas.detection.v1.DetectionRecord;
import pk.nust.cuas.track.domain.model.Track;
import pk.nust.cuas.track.domain.model.TrackClassification;

/**
 * Asks the AI boundary what a track is (ARCHITECTURE.md sections 4.1.5, 6.5).
 *
 * <p>The only synchronous call this service makes to another (CLAUDE.md §2 permits exactly this one:
 * {@code track-correlation → ai-orchestrator}). An output port, so the correlation pipeline never
 * names gRPC and never learns that an orchestrator exists — an adapter satisfies it.
 *
 * <p><b>Returns empty rather than throwing.</b> §5.6 is explicit that a drone detected but scored
 * late is a degraded outcome while a drone never ingested is an undetected one, and the same logic
 * applies here: a track that could not be classified is still a track. The adapter is
 * circuit-broken, so a failing orchestrator costs one fast rejection per call and the tracker carries
 * on unclassified.
 */
public interface TrackClassifier {

    /**
     * @param track     the track to classify
     * @param detection the detection that triggered this classification, which carries the modality
     *                  the orchestrator routes on
     * @return the classification, or empty when none could be obtained — never an exception
     */
    Optional<TrackClassification> classify(Track track, DetectionRecord detection);
}
