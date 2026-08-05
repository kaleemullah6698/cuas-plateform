package pk.nust.cuas.track.application;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import pk.nust.cuas.commons.correlation.CorrelationId;
import pk.nust.cuas.track.domain.model.Track;
import pk.nust.cuas.track.domain.port.TrackRepository;
import pk.nust.cuas.track.domain.service.TrackTimeoutPolicy;

/**
 * Sweeps stale tracks and transitions each one that is due (ARCHITECTURE.md section 4.1.5).
 *
 * <p>Finds the open tracks that have missed detections for at least the coast timeout — the earliest
 * any track can be due a transition — and applies each in its own transaction. A track that a
 * detection changed concurrently loses its optimistic-lock race here and is simply retried on the
 * next sweep. The cadence is a scheduling concern and lives in the infrastructure layer; this holds
 * only what to do each sweep, so it is testable without a scheduler.
 *
 * <p>A timeout transition is not tied to any detection, so each is given a fresh correlation id for
 * its logs and its published event.
 */
@Service
public class TrackTimeoutService {

    private static final Logger log = LoggerFactory.getLogger(TrackTimeoutService.class);

    private final TrackRepository tracks;
    private final TrackTimeoutPolicy timeoutPolicy;
    private final TrackTimeoutApplier applier;
    private final Clock clock;

    public TrackTimeoutService(TrackRepository tracks, TrackTimeoutPolicy timeoutPolicy,
                               TrackTimeoutApplier applier, Clock clock) {
        this.tracks = tracks;
        this.timeoutPolicy = timeoutPolicy;
        this.applier = applier;
        this.clock = clock;
    }

    /** Transitions every track that is due a coast or a loss. */
    public void sweep() {
        Instant now = clock.instant();
        Instant threshold = now.minus(timeoutPolicy.coastTimeout());
        List<Track> stale = tracks.findOpenStaleBefore(threshold);
        if (stale.isEmpty()) {
            return;
        }
        log.debug("Timeout sweep: {} stale track(s)", stale.size());
        for (Track track : stale) {
            CorrelationId.set(CorrelationId.generate());
            try {
                applier.apply(track.id(), now);
            } catch (OptimisticLockingFailureException ex) {
                log.debug("Track {} changed during the sweep; retrying next tick", track.id());
            } finally {
                CorrelationId.clear();
            }
        }
    }
}
