package pk.nust.cuas.track.infrastructure.messaging;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import pk.nust.cuas.track.application.TrackMergeService;

/**
 * Drives the cross-sensor merge sweep on a fixed cadence.
 *
 * <p>Separate from the timeout sweep and on its own interval, because the two answer different
 * questions on different timescales: the timeout sweep asks whether a track has gone quiet, this one
 * asks whether two tracks have stayed together. Its interval is also the unit the
 * consecutive-sweep requirement counts in, so the two must be tunable independently.
 *
 * <p>Like the timeout sweep, the trigger lives in infrastructure — scheduling is a delivery
 * mechanism, not a domain decision.
 */
@Component
public class TrackMergeScheduler {

    private final TrackMergeService mergeService;

    public TrackMergeScheduler(TrackMergeService mergeService) {
        this.mergeService = mergeService;
    }

    @Scheduled(fixedDelayString = "${correlation.merge.check-interval:PT5S}")
    public void sweep() {
        mergeService.sweep();
    }
}
