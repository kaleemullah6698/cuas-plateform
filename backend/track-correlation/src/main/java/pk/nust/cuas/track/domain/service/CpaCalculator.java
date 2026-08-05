package pk.nust.cuas.track.domain.service;

import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import pk.nust.cuas.track.domain.model.ClosestPointOfApproach;
import pk.nust.cuas.track.domain.model.GeoPoint;
import pk.nust.cuas.track.domain.model.ProtectedAsset;
import pk.nust.cuas.track.domain.model.Velocity;

/**
 * Computes how close a track will come to a protected asset (ARCHITECTURE.md sections 4.1.5, 4.3).
 *
 * <p>The asset is stationary and the track is treated as moving at its current estimated velocity,
 * so the closest point of approach has a closed form: in a local frame centred on the asset, with
 * relative position {@code r} and velocity {@code v}, the separation is minimised at
 * {@code t = -(r·v)/|v|²}. No search, no iteration, and no dependence on the prediction horizon —
 * which is why this is computed directly rather than by scanning the predicted trajectory for its
 * nearest point. Scanning would be bounded by the horizon and quantised by the step, and would
 * report a CPA at the horizon's edge for anything approaching more slowly than that.
 *
 * <p>Horizontal only. A CPA is the operator's "how close does this get to the thing I am guarding",
 * and folding altitude into it would make a high overflight of an asset read as distant when the
 * ground track passes directly over it. Altitude is published separately on the track's position.
 *
 * <p>Pure and framework-free: given the same state and assets it always returns the same answer.
 */
public final class CpaCalculator {

    private static final double EARTH_RADIUS_M = 6_371_000.0d;
    /** Below this, a track is not meaningfully moving and a CPA time would be numerically absurd. */
    private static final double MIN_SPEED_MS = 0.1d;

    /**
     * Finds the nearest closest-approach across every asset.
     *
     * <p>"Nearest" is by CPA distance, not by present distance: an asset the track will pass close to
     * matters more than one it happens to be near now and is moving away from. That is the question
     * threat-assessment (section 6.3.4) is asking.
     *
     * @param position   the track's current estimated position
     * @param velocity   its current estimated horizontal velocity
     * @param observedAt the time that estimate is valid for
     * @param assets     the protected assets to consider
     * @return the closest approach, or empty when no asset is configured
     */
    public Optional<ClosestPointOfApproach> nearest(
            GeoPoint position, Velocity velocity, Instant observedAt, List<ProtectedAsset> assets) {
        return assets.stream()
                .map(asset -> against(position, velocity, observedAt, asset))
                .min(Comparator.comparingDouble(ClosestPointOfApproach::distanceM));
    }

    /**
     * Computes the closest approach to one asset.
     *
     * @param position   the track's current estimated position
     * @param velocity   its current estimated horizontal velocity
     * @param observedAt the time that estimate is valid for
     * @param asset      the asset to measure against
     * @return the closest approach; already-receding and stationary tracks report the present
     *     separation with {@code closing = false}
     */
    public ClosestPointOfApproach against(
            GeoPoint position, Velocity velocity, Instant observedAt, ProtectedAsset asset) {
        double presentSeparation = position.distanceTo(asset.position());
        if (velocity.speedMs() < MIN_SPEED_MS) {
            return receding(asset, presentSeparation, observedAt);
        }

        // Local east-north frame centred on the asset: the track's offset from it, and its velocity.
        double latRad = Math.toRadians(asset.position().latitude());
        double rEast = Math.toRadians(position.longitude() - asset.position().longitude())
                * Math.cos(latRad) * EARTH_RADIUS_M;
        double rNorth = Math.toRadians(position.latitude() - asset.position().latitude()) * EARTH_RADIUS_M;

        double headingRad = Math.toRadians(velocity.headingDeg());
        double vEast = velocity.speedMs() * Math.sin(headingRad);
        double vNorth = velocity.speedMs() * Math.cos(headingRad);

        double speedSquared = vEast * vEast + vNorth * vNorth;
        double timeToCpa = -(rEast * vEast + rNorth * vNorth) / speedSquared;
        if (timeToCpa <= 0.0d) {
            // The minimum is in the past: the track is already at or past its closest point.
            return receding(asset, presentSeparation, observedAt);
        }

        double closestEast = rEast + vEast * timeToCpa;
        double closestNorth = rNorth + vNorth * timeToCpa;
        double distance = Math.hypot(closestEast, closestNorth);
        return new ClosestPointOfApproach(asset, distance, timeToCpa,
                observedAt.plus(Duration.ofNanos(Math.round(timeToCpa * 1_000_000_000.0d))), true);
    }

    private static ClosestPointOfApproach receding(ProtectedAsset asset, double separation, Instant now) {
        return new ClosestPointOfApproach(asset, separation, 0.0d, now, false);
    }
}
