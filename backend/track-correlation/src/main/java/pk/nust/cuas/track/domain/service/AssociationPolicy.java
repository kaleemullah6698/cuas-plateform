package pk.nust.cuas.track.domain.service;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import pk.nust.cuas.track.domain.model.GeoPoint;
import pk.nust.cuas.track.domain.model.Track;

/**
 * Chooses which existing track a detection belongs to, if any (ARCHITECTURE.md section 4.1.5,
 * spatio-temporal association).
 *
 * <p>Each candidate is offered with the position its Kalman filter <em>predicts</em> it should be at
 * the detection's time — predict-then-gate. A candidate qualifies if that predicted position is
 * within the distance gate of the detection, and among those the closest wins (nearest-neighbour). A
 * detection that matches no track starts a new one. Gating on the prediction rather than the last
 * fixed position is what lets a moving track keep associating as it moves, and lets a coasting track
 * be re-acquired where it is expected to be, not where it was last seen.
 *
 * <p>The temporal bound is applied upstream by the candidate query (a lost track, or one older than
 * the lost timeout, is never offered), so this policy is purely spatial and stays a pure function.
 */
public final class AssociationPolicy {

    private final double distanceGateM;

    /**
     * @param distanceGateM the maximum separation, in metres, between a detection and a track's
     *                      predicted position for the detection to extend that track
     * @throws IllegalArgumentException when the gate is not positive
     */
    public AssociationPolicy(double distanceGateM) {
        if (!(distanceGateM > 0.0d)) {
            throw new IllegalArgumentException("distance gate must be positive, was " + distanceGateM);
        }
        this.distanceGateM = distanceGateM;
    }

    /**
     * @param detectionPosition the detection's position
     * @param candidates        the open tracks in the detection's sector, each with its predicted position
     * @return the nearest track within the gate, or empty to start a new track
     */
    public Optional<Match> select(GeoPoint detectionPosition, List<Candidate> candidates) {
        return candidates.stream()
                .map(candidate -> new Match(candidate.track(),
                        candidate.predictedPosition().distanceTo(detectionPosition)))
                .filter(match -> match.distanceM() <= distanceGateM)
                .min(Comparator.comparingDouble(Match::distanceM));
    }

    /**
     * A track and where its filter predicts it to be at the detection's time.
     *
     * @param track             the candidate track
     * @param predictedPosition its predicted position at the detection's time
     */
    public record Candidate(Track track, GeoPoint predictedPosition) {
    }

    /**
     * A matched track and the separation between the detection and the track's predicted position.
     *
     * @param track     the matched track
     * @param distanceM the separation in metres
     */
    public record Match(Track track, double distanceM) {
    }
}
