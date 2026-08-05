package pk.nust.cuas.track.domain.service;

import pk.nust.cuas.track.domain.model.Axis;
import pk.nust.cuas.track.domain.model.GeoPoint;
import pk.nust.cuas.track.domain.model.KalmanState;
import pk.nust.cuas.track.domain.model.Velocity;

/**
 * A constant-acceleration Kalman filter for track state estimation (ARCHITECTURE.md section 4.1.5).
 *
 * <p>The filter runs one independent 3-state estimator — position, velocity, acceleration — per axis
 * of a local East-North-Up frame anchored at the track's first position, so degrees never enter the
 * dynamics and the three axes decouple. Prediction advances the estimate to a later time under a
 * constant-acceleration model with white-noise-jerk process noise; update corrects it against a
 * position measurement. Coasting is prediction without update.
 *
 * <p>Pure and deterministic: given the same state, elapsed time, and measurement it always yields the
 * same estimate, with no randomness and no framework — which is what makes it exhaustively testable.
 * The tuning (process noise, measurement noise, initial uncertainty) is fixed at construction.
 */
public final class KalmanFilter {

    private static final double EARTH_RADIUS_M = 6_371_000.0d;

    private final double processNoise;         // white-noise jerk PSD (q)
    private final double measurementNoise;     // position measurement variance (r), m²
    private final double initialPositionVariance;
    private final double initialVelocityVariance;
    private final double initialAccelerationVariance;

    public KalmanFilter(double processNoise, double measurementNoise,
                        double initialPositionVariance, double initialVelocityVariance,
                        double initialAccelerationVariance) {
        if (processNoise < 0 || measurementNoise <= 0) {
            throw new IllegalArgumentException("process noise must be >= 0 and measurement noise > 0");
        }
        this.processNoise = processNoise;
        this.measurementNoise = measurementNoise;
        this.initialPositionVariance = initialPositionVariance;
        this.initialVelocityVariance = initialVelocityVariance;
        this.initialAccelerationVariance = initialAccelerationVariance;
    }

    /**
     * Initialises the filter for a new track from its first detection.
     *
     * @param position the first observed position, which becomes the local frame origin
     * @param seed     the detection's reported velocity, used to seed the velocity estimate
     * @return the initial state: position at the origin, velocity from the seed, acceleration zero
     */
    public KalmanState initial(GeoPoint position, Velocity seed) {
        double headingRad = Math.toRadians(seed.headingDeg());
        double vEast = seed.speedMs() * Math.sin(headingRad);
        double vNorth = seed.speedMs() * Math.cos(headingRad);
        return new KalmanState(position,
                seededAxis(vEast), seededAxis(vNorth), seededAxis(0.0d));
    }

    /**
     * Advances the state to a later time and corrects it against a position measurement (the
     * predict-then-update cycle).
     *
     * @param state       the current state
     * @param dtSeconds   the elapsed time since the state's time; negative values are clamped to zero
     * @param measurement the observed position
     * @return the corrected state
     */
    public KalmanState step(KalmanState state, double dtSeconds, GeoPoint measurement) {
        double dt = Math.max(0.0d, dtSeconds);
        double[] enu = toEnu(state.origin(), measurement);
        return new KalmanState(state.origin(),
                update(predict(state.east(), dt), enu[0]),
                update(predict(state.north(), dt), enu[1]),
                update(predict(state.up(), dt), enu[2]));
    }

    /**
     * Advances the state to a later time without a measurement — dead reckoning, for a coasting track.
     *
     * @param state     the current state
     * @param dtSeconds the elapsed time since the state's time; negative values are clamped to zero
     * @return the predicted state, with grown uncertainty
     */
    public KalmanState predict(KalmanState state, double dtSeconds) {
        double dt = Math.max(0.0d, dtSeconds);
        return new KalmanState(state.origin(),
                predict(state.east(), dt), predict(state.north(), dt), predict(state.up(), dt));
    }

    /** @return the estimated geodetic position of a state */
    public GeoPoint position(KalmanState state) {
        return toGeo(state.origin(),
                state.east().position(), state.north().position(), state.up().position());
    }

    /** @return the estimated horizontal velocity (ground speed and heading) of a state */
    public Velocity velocity(KalmanState state) {
        double vEast = state.east().velocity();
        double vNorth = state.north().velocity();
        double speed = Math.hypot(vEast, vNorth);
        double heading = Math.toDegrees(Math.atan2(vEast, vNorth));
        if (heading < 0) {
            heading += 360.0d;
        }
        return new Velocity(speed, heading);
    }

    private Axis seededAxis(double velocity) {
        double[][] covariance = {
                {initialPositionVariance, 0, 0},
                {0, initialVelocityVariance, 0},
                {0, 0, initialAccelerationVariance}};
        return new Axis(0.0d, velocity, 0.0d, covariance);
    }

    private Axis predict(Axis axis, double dt) {
        double p = axis.position() + axis.velocity() * dt + 0.5 * axis.acceleration() * dt * dt;
        double v = axis.velocity() + axis.acceleration() * dt;
        double a = axis.acceleration();

        double[][] f = {{1, dt, 0.5 * dt * dt}, {0, 1, dt}, {0, 0, 1}};
        double[][] covariance = add(multiply(multiply(f, axis.covariance()), transpose(f)), processQ(dt));
        return new Axis(p, v, a, covariance);
    }

    private Axis update(Axis axis, double measurement) {
        double[][] cov = axis.covariance();
        double s = cov[0][0] + measurementNoise;      // innovation covariance (scalar, since H = [1,0,0])
        double k0 = cov[0][0] / s;
        double k1 = cov[1][0] / s;
        double k2 = cov[2][0] / s;
        double innovation = measurement - axis.position();

        double p = axis.position() + k0 * innovation;
        double v = axis.velocity() + k1 * innovation;
        double a = axis.acceleration() + k2 * innovation;

        // P = (I - K H) P, with H = [1,0,0]: subtract K times the first row of P.
        double[] k = {k0, k1, k2};
        double[][] updated = new double[3][3];
        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 3; j++) {
                updated[i][j] = cov[i][j] - k[i] * cov[0][j];
            }
        }
        return new Axis(p, v, a, updated);
    }

    /** White-noise-jerk process noise for the constant-acceleration model. */
    private double[][] processQ(double dt) {
        double dt2 = dt * dt;
        double dt3 = dt2 * dt;
        double dt4 = dt3 * dt;
        double dt5 = dt4 * dt;
        return new double[][] {
                {processNoise * dt5 / 20.0, processNoise * dt4 / 8.0, processNoise * dt3 / 6.0},
                {processNoise * dt4 / 8.0, processNoise * dt3 / 3.0, processNoise * dt2 / 2.0},
                {processNoise * dt3 / 6.0, processNoise * dt2 / 2.0, processNoise * dt}};
    }

    private static double[][] multiply(double[][] a, double[][] b) {
        double[][] result = new double[3][3];
        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 3; j++) {
                double sum = 0.0d;
                for (int k = 0; k < 3; k++) {
                    sum += a[i][k] * b[k][j];
                }
                result[i][j] = sum;
            }
        }
        return result;
    }

    private static double[][] transpose(double[][] a) {
        return new double[][] {
                {a[0][0], a[1][0], a[2][0]},
                {a[0][1], a[1][1], a[2][1]},
                {a[0][2], a[1][2], a[2][2]}};
    }

    private static double[][] add(double[][] a, double[][] b) {
        double[][] result = new double[3][3];
        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 3; j++) {
                result[i][j] = a[i][j] + b[i][j];
            }
        }
        return result;
    }

    private static double[] toEnu(GeoPoint origin, GeoPoint point) {
        double east = Math.toRadians(point.longitude() - origin.longitude())
                * Math.cos(Math.toRadians(origin.latitude())) * EARTH_RADIUS_M;
        double north = Math.toRadians(point.latitude() - origin.latitude()) * EARTH_RADIUS_M;
        double up = point.altitudeM() - origin.altitudeM();
        return new double[] {east, north, up};
    }

    private static GeoPoint toGeo(GeoPoint origin, double east, double north, double up) {
        double latitude = origin.latitude() + Math.toDegrees(north / EARTH_RADIUS_M);
        double longitude = origin.longitude()
                + Math.toDegrees(east / (EARTH_RADIUS_M * Math.cos(Math.toRadians(origin.latitude()))));
        return new GeoPoint(latitude, longitude, origin.altitudeM() + up);
    }
}
