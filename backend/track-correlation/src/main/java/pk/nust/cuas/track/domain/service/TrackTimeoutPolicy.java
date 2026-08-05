package pk.nust.cuas.track.domain.service;

import java.time.Duration;
import java.time.Instant;
import pk.nust.cuas.track.domain.model.TrackStatus;

/**
 * Decides the lifecycle transition a track is due, given how long it has been since a detection last
 * fed it (ARCHITECTURE.md section 4.1.5: confirmed → coasting → lost).
 *
 * <p>Two timeouts measured from the last detection: a confirmed track that misses for longer than the
 * coast timeout starts coasting; any open track that misses for longer than the lost timeout is
 * lost. A tentative track that never confirmed is dropped straight to lost at the lost timeout — it
 * does not coast, because there is no confirmed trajectory to predict. Pure, so the decision is
 * tested without a scheduler or a database.
 */
public final class TrackTimeoutPolicy {

    /** The action a track is due, if any. */
    public enum Action {
        /** No transition is due. */
        NONE,
        /** A confirmed track should start coasting. */
        COAST,
        /** An open track should be marked lost. */
        LOSE
    }

    private final Duration coastTimeout;
    private final Duration lostTimeout;

    /**
     * @param coastTimeout how long a confirmed track may miss detections before it coasts
     * @param lostTimeout  how long any open track may miss detections before it is lost
     * @throws IllegalArgumentException when the lost timeout is not longer than the coast timeout
     */
    public TrackTimeoutPolicy(Duration coastTimeout, Duration lostTimeout) {
        if (lostTimeout.compareTo(coastTimeout) <= 0) {
            throw new IllegalArgumentException(
                    "lost timeout (" + lostTimeout + ") must exceed coast timeout (" + coastTimeout + ")");
        }
        this.coastTimeout = coastTimeout;
        this.lostTimeout = lostTimeout;
    }

    /**
     * @param status          the track's current status
     * @param lastDetectionAt when a detection last fed the track
     * @param now             the current time
     * @return the transition due
     */
    public Action evaluate(TrackStatus status, Instant lastDetectionAt, Instant now) {
        if (!status.isOpen()) {
            return Action.NONE;
        }
        Duration age = Duration.between(lastDetectionAt, now);
        if (age.compareTo(lostTimeout) > 0) {
            return Action.LOSE;
        }
        if (status == TrackStatus.CONFIRMED && age.compareTo(coastTimeout) > 0) {
            return Action.COAST;
        }
        return Action.NONE;
    }

    /** The lost timeout, so a caller can bound its candidate query to the same window. */
    public Duration lostTimeout() {
        return lostTimeout;
    }

    /** The coast timeout — the earliest any track can be due a transition, for the sweep's query. */
    public Duration coastTimeout() {
        return coastTimeout;
    }
}
