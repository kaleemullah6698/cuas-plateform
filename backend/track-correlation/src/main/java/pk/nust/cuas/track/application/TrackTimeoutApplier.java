package pk.nust.cuas.track.application;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pk.nust.cuas.track.application.port.TrackEventPublisher;
import pk.nust.cuas.track.domain.model.Track;
import pk.nust.cuas.track.domain.port.HotTrackStateStore;
import pk.nust.cuas.track.domain.port.TrackRepository;
import pk.nust.cuas.track.domain.service.KalmanFilter;
import pk.nust.cuas.track.domain.service.TrackOutlookCalculator;
import pk.nust.cuas.track.domain.service.TrackTimeoutPolicy;

/**
 * Applies the due lifecycle transition to a single track, in its own transaction (ARCHITECTURE.md
 * section 4.1.5).
 *
 * <p>Separate from the sweep so each track's transition commits independently: a track that changed
 * under a concurrent detection (an optimistic-lock conflict) fails only its own transaction, and the
 * sweep moves on. The track is re-read here rather than trusted from the sweep's snapshot, so the
 * decision is made against its current state. Persist before publish, so a publish failure rolls the
 * transition back.
 */
@Service
public class TrackTimeoutApplier {

    private static final Logger log = LoggerFactory.getLogger(TrackTimeoutApplier.class);

    private final TrackRepository tracks;
    private final HotTrackStateStore hotState;
    private final TrackTimeoutPolicy timeoutPolicy;
    private final KalmanFilter kalman;
    private final TrackEventPublisher publisher;
    private final TrackOutlookCalculator outlook;

    public TrackTimeoutApplier(TrackRepository tracks, HotTrackStateStore hotState, TrackTimeoutPolicy timeoutPolicy,
                               KalmanFilter kalman, TrackEventPublisher publisher,
                               TrackOutlookCalculator outlook) {
        this.tracks = tracks;
        this.hotState = hotState;
        this.timeoutPolicy = timeoutPolicy;
        this.kalman = kalman;
        this.publisher = publisher;
        this.outlook = outlook;
    }

    /**
     * Re-reads the track and applies whatever transition it is now due.
     *
     * @param trackId the track to check
     * @param now     the sweep time
     */
    @Transactional
    public void apply(UUID trackId, Instant now) {
        Optional<Track> maybe = tracks.findById(trackId);
        if (maybe.isEmpty()) {
            return;
        }
        Track track = maybe.get();
        switch (timeoutPolicy.evaluate(track.status(), track.lastDetectionAt(), now)) {
            case COAST -> coast(track, now);
            case LOSE -> lose(track, now);
            case NONE -> { /* the track was fed a detection since the sweep snapshot; nothing to do */ }
        }
    }

    private void coast(Track track, Instant now) {
        Track.Transition transition = track.coast(now, kalman);
        tracks.coast(transition.track(), transition.lifecycleEvent().orElseThrow());
        // Still open, so it stays a candidate — but at its newly predicted position.
        hotState.put(transition.track());
        publisher.publishUpdated(transition.track(), outlook.outlookFor(transition.track()));
        log.debug("Track {} is coasting", track.id());
    }

    private void lose(Track track, Instant now) {
        Track.Transition transition = track.markLost(now, "no detection within the lost timeout");
        tracks.lose(transition.track(), transition.lifecycleEvent().orElseThrow());
        // Terminal: leaving it cached would offer the pipeline a track it must not associate to.
        hotState.evict(track.sector(), track.id());
        publisher.publishLost(transition.track());
        log.debug("Track {} is lost", track.id());
    }
}
