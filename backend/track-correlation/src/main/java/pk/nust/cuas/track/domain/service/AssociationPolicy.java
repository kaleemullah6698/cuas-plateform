package pk.nust.cuas.track.domain.service;

import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import pk.nust.cuas.track.domain.model.GeoPoint;
import pk.nust.cuas.track.domain.model.Track;

/**
 * Chooses which existing track a detection belongs to, if any (ARCHITECTURE.md section 4.1.5,
 * spatio-temporal association).
 *
 * <p>The foundation gates on space and time and picks the nearest survivor: a candidate track
 * qualifies if its last observed state is within the distance gate of the detection and within the
 * time gate of the detection's time, and among those the closest wins (nearest-neighbour). A
 * detection that matches no track starts a new one. This is deliberately a static gate with no
 * motion model — the Kalman predict-then-gate that makes association robust to speed and manoeuvre,
 * and cross-sensor fusion, are M5.2. Pure, so it is exhaustively testable without a database.
 */
public final class AssociationPolicy {

    private final double distanceGateM;
    private final Duration timeGate;

    /**
     * @param distanceGateM the maximum separation, in metres, for a detection to extend a track
     * @param timeGate      the maximum time gap between a track's last state and the detection
     * @throws IllegalArgumentException when either gate is not positive
     */
    public AssociationPolicy(double distanceGateM, Duration timeGate) {
        if (!(distanceGateM > 0.0d)) {
            throw new IllegalArgumentException("distance gate must be positive, was " + distanceGateM);
        }
        if (timeGate.isNegative() || timeGate.isZero()) {
            throw new IllegalArgumentException("time gate must be positive, was " + timeGate);
        }
        this.distanceGateM = distanceGateM;
        this.timeGate = timeGate;
    }

    /**
     * @param position   the detection's position
     * @param observedAt the detection's ingest time
     * @param candidates the open tracks in the detection's sector to consider
     * @return the nearest track within both gates, or empty to start a new track
     */
    public Optional<Match> select(GeoPoint position, Instant observedAt, List<Track> candidates) {
        return candidates.stream()
                .filter(track -> withinTimeGate(track.lastSeen(), observedAt))
                .map(track -> new Match(track, track.state().position().distanceTo(position)))
                .filter(match -> match.distanceM() <= distanceGateM)
                .min(Comparator.comparingDouble(Match::distanceM));
    }

    private boolean withinTimeGate(Instant lastSeen, Instant observedAt) {
        return Duration.between(lastSeen, observedAt).abs().compareTo(timeGate) <= 0;
    }

    /** The time gate, so a caller can bound its candidate query to the same window. */
    public Duration timeGate() {
        return timeGate;
    }

    /**
     * A candidate track and its distance from the detection.
     *
     * @param track     the matched track
     * @param distanceM the separation in metres
     */
    public record Match(Track track, double distanceM) {
    }
}
