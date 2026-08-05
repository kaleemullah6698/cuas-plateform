package pk.nust.cuas.track.application;

import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pk.nust.cuas.track.application.port.TrackEventPublisher;
import pk.nust.cuas.track.domain.model.Track;
import pk.nust.cuas.track.domain.model.TrackClassification;
import pk.nust.cuas.track.domain.port.TrackRepository;

/**
 * Records a classification against a track and announces it (ARCHITECTURE.md section 4.1.5).
 *
 * <p>Fed from {@code ai.inference.completed.v1} — the orchestrator's result arriving asynchronously —
 * and publishes {@code track.classified.v1}.
 *
 * <p><b>Why consume the topic when the call is synchronous?</b> Because they are not the same
 * information. The synchronous {@code Infer} returns the single best answer so the track can be
 * enriched immediately; the topic carries <em>every</em> service's result, including ones that arrived
 * after the call's deadline or were produced for a track this instance did not ask about. §4.1.5
 * lists both, and consuming the topic is what makes a late or out-of-band inference still reach the
 * track it belongs to.
 *
 * <p>A classification for an unknown or terminal track is discarded rather than treated as an error:
 * inference is asynchronous, so a result can outlive the track it describes, and a lost track that
 * came back to life because a late inference arrived would be a genuine defect.
 */
@Service
public class TrackClassificationService {

    private static final Logger log = LoggerFactory.getLogger(TrackClassificationService.class);

    private final TrackRepository tracks;
    private final TrackEventPublisher publisher;

    public TrackClassificationService(TrackRepository tracks, TrackEventPublisher publisher) {
        this.tracks = tracks;
        this.publisher = publisher;
    }

    /**
     * Applies a classification to a track.
     *
     * @param trackId        the track it is about
     * @param classification what the AI boundary concluded
     */
    @Transactional
    public void apply(UUID trackId, TrackClassification classification) {
        Optional<Track> maybe = tracks.findById(trackId);
        if (maybe.isEmpty()) {
            log.debug("Classification for unknown track {}; ignoring", trackId);
            return;
        }
        Track track = maybe.get();
        if (!track.status().isOpen()) {
            // Inference is asynchronous; a result can outlive the track it describes. Reviving a
            // lost or merged track because a late inference arrived would be a real defect.
            log.debug("Classification for {} track {}; ignoring", track.status(), trackId);
            return;
        }

        publisher.publishClassified(track, classification);
        log.debug("Track {} classified as {} ({}) by {} {}", trackId, classification.label(),
                String.format("%.2f", classification.confidence()),
                classification.modelId(), classification.modelVersion());
    }
}
