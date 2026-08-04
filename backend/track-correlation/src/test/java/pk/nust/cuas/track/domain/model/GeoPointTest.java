package pk.nust.cuas.track.domain.model;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class GeoPointTest {

    @Test
    void distanceToItselfIsZero() {
        GeoPoint point = new GeoPoint(33.6844, 73.0479, 100.0);
        assertThat(point.distanceTo(point)).isZero();
    }

    @Test
    void oneDegreeOfLatitudeIsAboutOneHundredAndElevenKilometres() {
        GeoPoint a = new GeoPoint(33.0, 73.0, 0.0);
        GeoPoint b = new GeoPoint(34.0, 73.0, 0.0);

        // A degree of latitude is ~111.19 km on a spherical earth.
        assertThat(a.distanceTo(b)).isCloseTo(111_195.0, org.assertj.core.data.Offset.offset(200.0));
    }

    @Test
    void aShortHorizontalSeparationIsMeasuredInMetres() {
        // ~0.001 degree of longitude at this latitude is on the order of 90 m.
        GeoPoint a = new GeoPoint(33.6844, 73.0479, 100.0);
        GeoPoint b = new GeoPoint(33.6844, 73.0489, 100.0);

        assertThat(a.distanceTo(b)).isBetween(80.0, 100.0);
    }

    @Test
    void distanceIsSymmetric() {
        GeoPoint a = new GeoPoint(33.68, 73.04, 0.0);
        GeoPoint b = new GeoPoint(33.70, 73.10, 0.0);

        assertThat(a.distanceTo(b)).isEqualTo(b.distanceTo(a));
    }
}
