package pk.nust.cuas.track.domain.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.assertj.core.data.Offset;
import org.junit.jupiter.api.Test;
import pk.nust.cuas.track.domain.model.GeoPoint;
import pk.nust.cuas.track.domain.model.KalmanState;
import pk.nust.cuas.track.domain.model.Velocity;

/**
 * The Kalman filter is a pure function, so every case here is fully deterministic — fixed inputs,
 * exact (noise-free) measurements, and asserted-to-tolerance outputs.
 */
class KalmanFilterTest {

    private static final double EARTH_RADIUS_M = 6_371_000.0d;
    private static final GeoPoint ORIGIN = new GeoPoint(33.6844, 73.0479, 100.0);

    private final KalmanFilter filter = new KalmanFilter(1.0, 25.0, 100.0, 100.0, 10.0);

    private GeoPoint metresNorthOf(GeoPoint from, double metres) {
        return new GeoPoint(from.latitude() + Math.toDegrees(metres / EARTH_RADIUS_M),
                from.longitude(), from.altitudeM());
    }

    @Test
    void theInitialStateSitsAtTheMeasurementWithTheSeedVelocity() {
        KalmanState state = filter.initial(ORIGIN, new Velocity(10.0, 0.0)); // 10 m/s due north

        GeoPoint position = filter.position(state);
        assertThat(position.latitude()).isCloseTo(ORIGIN.latitude(), Offset.offset(1e-9));
        assertThat(position.longitude()).isCloseTo(ORIGIN.longitude(), Offset.offset(1e-9));

        Velocity velocity = filter.velocity(state);
        assertThat(velocity.speedMs()).isCloseTo(10.0, Offset.offset(1e-6));
        assertThat(velocity.headingDeg()).isCloseTo(0.0, Offset.offset(1e-6));
    }

    @Test
    void aStationaryTargetConvergesTowardsZeroVelocity() {
        KalmanState state = filter.initial(ORIGIN, new Velocity(5.0, 45.0)); // wrong seed, on purpose
        for (int i = 1; i <= 30; i++) {
            state = filter.step(state, 1.0, ORIGIN); // the same place, every second
        }

        assertThat(filter.velocity(state).speedMs()).isLessThan(0.5);
        assertThat(filter.position(state).latitude()).isCloseTo(ORIGIN.latitude(), Offset.offset(1e-6));
    }

    @Test
    void tracksConstantVelocityMotion() {
        double speed = 12.0; // m/s due north
        KalmanState state = filter.initial(ORIGIN, new Velocity(speed, 0.0));
        for (int t = 1; t <= 20; t++) {
            state = filter.step(state, 1.0, metresNorthOf(ORIGIN, speed * t));
        }

        Velocity velocity = filter.velocity(state);
        assertThat(velocity.speedMs()).isCloseTo(speed, Offset.offset(1.0));
        assertThat(velocity.headingDeg()).isCloseTo(0.0, Offset.offset(2.0));
        // The estimate sits on the latest measurement, 240 m north of the origin.
        assertThat(filter.position(state).latitude())
                .isCloseTo(metresNorthOf(ORIGIN, speed * 20).latitude(), Offset.offset(1e-4));
    }

    @Test
    void predictionAdvancesPositionByVelocityWithoutAMeasurement() {
        KalmanState moving = filter.initial(ORIGIN, new Velocity(10.0, 0.0)); // 10 m/s north

        KalmanState predicted = filter.predict(moving, 5.0);

        // Dead reckoning 5 s north at 10 m/s ≈ 50 m north.
        double metresNorth = (filter.position(predicted).latitude() - ORIGIN.latitude())
                * Math.toRadians(1) * EARTH_RADIUS_M;
        assertThat(metresNorth).isCloseTo(50.0, Offset.offset(1.0));
    }

    @Test
    void aNegativeElapsedTimeIsClampedToZero() {
        KalmanState state = filter.initial(ORIGIN, new Velocity(10.0, 0.0));

        // Predicting "backwards" must not move the estimate or blow up the covariance.
        assertThat(filter.position(filter.predict(state, -5.0)).latitude())
                .isCloseTo(ORIGIN.latitude(), Offset.offset(1e-9));
    }

    @Test
    void isDeterministic() {
        KalmanState a = filter.initial(ORIGIN, new Velocity(8.0, 30.0));
        KalmanState b = filter.initial(ORIGIN, new Velocity(8.0, 30.0));
        for (int i = 1; i <= 10; i++) {
            a = filter.step(a, 1.0, metresNorthOf(ORIGIN, 8.0 * i));
            b = filter.step(b, 1.0, metresNorthOf(ORIGIN, 8.0 * i));
        }

        assertThat(filter.position(a).latitude()).isEqualTo(filter.position(b).latitude());
        assertThat(filter.velocity(a).speedMs()).isEqualTo(filter.velocity(b).speedMs());
    }

    @Test
    void rejectsInvalidTuning() {
        assertThatThrownBy(() -> new KalmanFilter(-1.0, 25.0, 100, 100, 10))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new KalmanFilter(1.0, 0.0, 100, 100, 10))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
