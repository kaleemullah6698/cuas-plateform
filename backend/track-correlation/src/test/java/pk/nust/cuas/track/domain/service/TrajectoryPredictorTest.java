package pk.nust.cuas.track.domain.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import pk.nust.cuas.track.domain.model.GeoPoint;
import pk.nust.cuas.track.domain.model.KalmanState;
import pk.nust.cuas.track.domain.model.PredictedPoint;
import pk.nust.cuas.track.domain.model.Velocity;

class TrajectoryPredictorTest {

    private static final Instant T = Instant.parse("2026-08-04T12:00:00Z");
    private static final GeoPoint ORIGIN = new GeoPoint(33.0, 73.0, 100);

    private final KalmanFilter kalman = new KalmanFilter(1.0, 25.0, 100, 100, 10);
    private final TrajectoryPredictor predictor =
            new TrajectoryPredictor(kalman, Duration.ofSeconds(30), Duration.ofSeconds(5));

    /** A track moving due east at 20 m/s. */
    private KalmanState eastbound() {
        return kalman.initial(ORIGIN, new Velocity(20.0, 90.0));
    }

    @Test
    void projectsOnePointPerStepAcrossTheHorizon() {
        List<PredictedPoint> points = predictor.predict(eastbound(), T);

        assertThat(points).hasSize(6);   // 30 s horizon / 5 s step
        assertThat(points.get(0).at()).isEqualTo(T.plusSeconds(5));
        assertThat(points.get(5).at()).isEqualTo(T.plusSeconds(30));
    }

    @Test
    void theTrajectoryStartsOneStepAheadNotAtThePresent() {
        // The present position is already published as the track's own position; repeating it as the
        // first predicted point would double it on the operator's map.
        assertThat(predictor.predict(eastbound(), T).get(0).at()).isNotEqualTo(T);
    }

    @Test
    void aTrackMovingEastIsPredictedProgressivelyFurtherEast() {
        List<PredictedPoint> points = predictor.predict(eastbound(), T);

        assertThat(points).extracting(p -> p.position().longitude()).isSorted();
        assertThat(points).allSatisfy(p ->
                assertThat(p.position().latitude()).isCloseTo(ORIGIN.latitude(), within(1e-6)));
        // 20 m/s for 30 s is 600 m east of the origin.
        assertThat(points.get(5).position().distanceTo(ORIGIN)).isCloseTo(600.0d, within(5.0d));
    }

    @Test
    void uncertaintyGrowsWithTheHorizon() {
        List<PredictedPoint> points = predictor.predict(eastbound(), T);

        assertThat(points).extracting(PredictedPoint::uncertaintyM).isSorted();
        assertThat(points.get(5).uncertaintyM())
                .as("a 30 s projection is meaningfully less certain than a 5 s one")
                .isGreaterThan(points.get(0).uncertaintyM());
    }

    @Test
    void aStepLongerThanTheHorizonIsRejected() {
        assertThatThrownBy(() ->
                new TrajectoryPredictor(kalman, Duration.ofSeconds(5), Duration.ofSeconds(30)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void aZeroStepIsRejected() {
        assertThatThrownBy(() -> new TrajectoryPredictor(kalman, Duration.ofSeconds(30), Duration.ZERO))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
