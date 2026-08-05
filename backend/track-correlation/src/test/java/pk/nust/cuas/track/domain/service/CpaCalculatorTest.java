package pk.nust.cuas.track.domain.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import pk.nust.cuas.track.domain.model.ClosestPointOfApproach;
import pk.nust.cuas.track.domain.model.GeoPoint;
import pk.nust.cuas.track.domain.model.ProtectedAsset;
import pk.nust.cuas.track.domain.model.Velocity;

class CpaCalculatorTest {

    private static final Instant T = Instant.parse("2026-08-04T12:00:00Z");
    private static final double EARTH_RADIUS_M = 6_371_000.0d;

    private final CpaCalculator calculator = new CpaCalculator();

    private static final GeoPoint ASSET_AT = new GeoPoint(33.0, 73.0, 0);
    private static final ProtectedAsset ASSET = new ProtectedAsset("a1", "North Gate", ASSET_AT);

    /** A point {@code metres} east of the asset, so the geometry is exactly known. */
    private static GeoPoint east(double metres) {
        double degrees = Math.toDegrees(metres / (EARTH_RADIUS_M * Math.cos(Math.toRadians(ASSET_AT.latitude()))));
        return new GeoPoint(ASSET_AT.latitude(), ASSET_AT.longitude() + degrees, 100);
    }

    /** A point {@code metres} north of the asset. */
    private static GeoPoint north(double metres) {
        return new GeoPoint(ASSET_AT.latitude() + Math.toDegrees(metres / EARTH_RADIUS_M),
                ASSET_AT.longitude(), 100);
    }

    @Test
    void aTrackFlyingStraightAtAnAssetClosesToZero() {
        // 1000 m east of the asset, heading due west (270°) at 20 m/s: a head-on approach.
        ClosestPointOfApproach cpa =
                calculator.against(east(1000), new Velocity(20.0, 270.0), T, ASSET);

        assertThat(cpa.closing()).isTrue();
        assertThat(cpa.distanceM()).isCloseTo(0.0d, within(1.0d));
        assertThat(cpa.timeToCpaS()).isCloseTo(50.0d, within(0.5d));   // 1000 m / 20 m/s
        assertThat(cpa.at()).isCloseTo(T.plusSeconds(50), within(1000L, java.time.temporal.ChronoUnit.MILLIS));
    }

    @Test
    void aTrackPassingBesideAnAssetClosesToTheOffset() {
        // 1000 m east and 300 m north, heading due west: it passes 300 m to the north of the asset.
        GeoPoint start = new GeoPoint(north(300).latitude(), east(1000).longitude(), 100);

        ClosestPointOfApproach cpa = calculator.against(start, new Velocity(20.0, 270.0), T, ASSET);

        assertThat(cpa.closing()).isTrue();
        assertThat(cpa.distanceM()).isCloseTo(300.0d, within(2.0d));
        assertThat(cpa.timeToCpaS()).isCloseTo(50.0d, within(0.5d));
    }

    @Test
    void aRecedingTrackReportsThePresentSeparationAndIsNotClosing() {
        // 1000 m east, heading due east (90°): moving away from the first instant.
        ClosestPointOfApproach cpa =
                calculator.against(east(1000), new Velocity(20.0, 90.0), T, ASSET);

        assertThat(cpa.closing()).isFalse();
        assertThat(cpa.distanceM()).isCloseTo(1000.0d, within(2.0d));
        assertThat(cpa.timeToCpaS()).isZero();
        assertThat(cpa.at()).isEqualTo(T);
    }

    @Test
    void aTrackAlreadyAtItsClosestPointIsNotClosing() {
        // Directly north of the asset, heading due east: it is at the minimum right now.
        ClosestPointOfApproach cpa = calculator.against(north(300), new Velocity(20.0, 90.0), T, ASSET);

        assertThat(cpa.closing()).isFalse();
        assertThat(cpa.distanceM()).isCloseTo(300.0d, within(2.0d));
    }

    @Test
    void aStationaryTrackReportsThePresentSeparationRatherThanAnAbsurdTime() {
        // Dividing by a near-zero speed would give a meaningless time; the calculator refuses to.
        ClosestPointOfApproach cpa = calculator.against(east(500), Velocity.NONE, T, ASSET);

        assertThat(cpa.closing()).isFalse();
        assertThat(cpa.distanceM()).isCloseTo(500.0d, within(2.0d));
        assertThat(cpa.timeToCpaS()).isZero();
    }

    @Test
    void theNearestAssetIsTheOneItGetsClosestTo_notTheOneItIsNearestNow() {
        // "Far" is 5 km east; the track starts beside "near" but is flying straight at "far".
        ProtectedAsset near = new ProtectedAsset("near", "Near", east(100));
        ProtectedAsset far = new ProtectedAsset("far", "Far", east(5000));
        GeoPoint start = east(200);

        // Heading due east at 20 m/s: it leaves `near` behind and arrives at `far`.
        var cpa = calculator.nearest(start, new Velocity(20.0, 90.0), T, List.of(near, far));

        assertThat(cpa).isPresent();
        assertThat(cpa.get().asset().assetId())
                .as("the asset it will get closest to, not the one it is closest to now")
                .isEqualTo("far");
        assertThat(cpa.get().closing()).isTrue();
    }

    @Test
    void noAssetsMeansNoCpa() {
        assertThat(calculator.nearest(east(100), new Velocity(20.0, 270.0), T, List.of())).isEmpty();
    }
}
