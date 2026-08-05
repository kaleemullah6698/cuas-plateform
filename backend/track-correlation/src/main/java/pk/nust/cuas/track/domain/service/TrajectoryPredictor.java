package pk.nust.cuas.track.domain.service;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import pk.nust.cuas.track.domain.model.KalmanState;
import pk.nust.cuas.track.domain.model.PredictedPoint;

/**
 * Extrapolates where a track is going (ARCHITECTURE.md sections 4.1.5, 4.3).
 *
 * <p>Prediction is the filter's own {@code predict} step run forward in fixed increments with no
 * measurement to correct it — dead reckoning under the same constant-acceleration model the tracker
 * uses. Reusing the filter rather than re-deriving the motion here means the predicted path is
 * exactly the path the tracker believes in, and the growing covariance comes out for free: each
 * point carries the 1σ position error at its own horizon, so a consumer can tell a confident
 * two-second projection from a speculative thirty-second one.
 *
 * <p>Section 6.3.5 anticipates a learned {@code trajectory-predictor} service replacing this
 * extrapolation. It would produce the same {@link PredictedPoint} list, so nothing downstream
 * changes when it does.
 */
public final class TrajectoryPredictor {

    private final KalmanFilter kalman;
    private final Duration horizon;
    private final Duration step;

    /**
     * @param kalman  the filter arithmetic, so the prediction matches the tracker's motion model
     * @param horizon how far ahead to project
     * @param step    the interval between predicted points
     * @throws IllegalArgumentException when the step is not positive or exceeds the horizon
     */
    public TrajectoryPredictor(KalmanFilter kalman, Duration horizon, Duration step) {
        if (step.isNegative() || step.isZero()) {
            throw new IllegalArgumentException("prediction step must be positive, was " + step);
        }
        if (horizon.compareTo(step) < 0) {
            throw new IllegalArgumentException(
                    "horizon (" + horizon + ") must be at least one step (" + step + ")");
        }
        this.kalman = kalman;
        this.horizon = horizon;
        this.step = step;
    }

    /**
     * Projects the track forward over the horizon.
     *
     * @param state the current filter state
     * @param from  the time that state is valid for — the trajectory starts one step after this
     * @return the predicted points in ascending time order; never includes {@code from} itself,
     *     because the present position is already published as the track's own position
     */
    public List<PredictedPoint> predict(KalmanState state, Instant from) {
        List<PredictedPoint> points = new ArrayList<>();
        double stepSeconds = step.toNanos() / 1_000_000_000.0d;
        long steps = horizon.toNanos() / step.toNanos();
        for (long i = 1; i <= steps; i++) {
            // Predict from the current state each time rather than compounding the previous
            // prediction: one advance of i*step accumulates process noise once for that interval,
            // whereas chaining i single steps would accumulate it i times and overstate the growth.
            KalmanState at = kalman.predict(state, stepSeconds * i);
            points.add(new PredictedPoint(
                    kalman.position(at), from.plus(step.multipliedBy(i)), kalman.positionUncertaintyM(at)));
        }
        return List.copyOf(points);
    }
}
