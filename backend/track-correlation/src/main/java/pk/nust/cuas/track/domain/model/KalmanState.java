package pk.nust.cuas.track.domain.model;

/**
 * The complete Kalman-filter state of a track (ARCHITECTURE.md section 4.1.5): a local origin and one
 * constant-acceleration {@link Axis} estimate per spatial direction.
 *
 * <p>The filter runs in a local East-North-Up tangent frame anchored at {@code origin} — the track's
 * first observed position — so the axes are decoupled metres, not coupled degrees. Persisting this
 * whole value, covariance included, is what lets the filter continue correctly across detections and
 * across a restart rather than starting cold each time (the scope's "persist complete track state").
 *
 * @param origin the geodetic anchor of the local frame
 * @param east   the East-axis estimate, metres east of the origin
 * @param north  the North-axis estimate, metres north of the origin
 * @param up     the Up-axis estimate, metres above the origin
 */
public record KalmanState(GeoPoint origin, Axis east, Axis north, Axis up) {

    public KalmanState {
        if (origin == null || east == null || north == null || up == null) {
            throw new IllegalArgumentException("origin and all three axes are required");
        }
    }
}
