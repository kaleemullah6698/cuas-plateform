package pk.nust.cuas.track.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import pk.nust.cuas.track.domain.model.GeoPoint;
import pk.nust.cuas.track.domain.model.KalmanState;
import pk.nust.cuas.track.domain.service.KalmanFilter;
import pk.nust.cuas.track.domain.model.Velocity;

class KalmanStateCodecTest {

    private final KalmanFilter filter = new KalmanFilter(1.0, 25.0, 100, 100, 10);

    @Test
    void roundTripsAStateExactly() {
        KalmanState original = filter.step(
                filter.initial(new GeoPoint(33.6844, 73.0479, 100), new Velocity(12, 45)),
                2.0, new GeoPoint(33.6850, 73.0485, 105));

        KalmanState decoded = KalmanStateCodec.decode(KalmanStateCodec.encode(original));

        assertThat(decoded.origin()).isEqualTo(original.origin());
        assertThat(decoded.east().position()).isEqualTo(original.east().position());
        assertThat(decoded.east().velocity()).isEqualTo(original.east().velocity());
        assertThat(decoded.north().covariance()).isDeepEqualTo(original.north().covariance());
        assertThat(decoded.up().covariance()).isDeepEqualTo(original.up().covariance());
        // The reconstituted estimate is bit-identical, so the filter resumes precisely.
        assertThat(filter.position(decoded).latitude()).isEqualTo(filter.position(original).latitude());
    }

    @Test
    void rejectsAnUnrecognisedFormat() {
        assertThatThrownBy(() -> KalmanStateCodec.decode("garbage"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> KalmanStateCodec.decode("v2,1,2,3"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
