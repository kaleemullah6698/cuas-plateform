package pk.nust.cuas.track.infrastructure.messaging;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import pk.nust.cuas.track.application.TrackTimeoutService;

/**
 * Drives the track timeout sweep on a fixed cadence.
 *
 * <p>The trigger lives in the infrastructure layer because scheduling is a delivery mechanism, not a
 * domain decision: {@link TrackTimeoutService} decides which tracks to coast or lose, this decides
 * how often to look. The interval is {@code correlation.lifecycle.check-interval}.
 */
@Component
public class TrackTimeoutScheduler {

    private final TrackTimeoutService timeoutService;

    public TrackTimeoutScheduler(TrackTimeoutService timeoutService) {
        this.timeoutService = timeoutService;
    }

    @Scheduled(fixedDelayString = "${correlation.lifecycle.check-interval:PT5S}")
    public void sweep() {
        timeoutService.sweep();
    }
}
