package pk.nust.cuas.track.domain.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import pk.nust.cuas.track.domain.model.GeoPoint;
import pk.nust.cuas.track.domain.model.KalmanState;
import pk.nust.cuas.track.domain.model.ProtectedAsset;
import pk.nust.cuas.track.domain.model.SectorId;
import pk.nust.cuas.track.domain.model.Track;
import pk.nust.cuas.track.domain.model.TrackOutlook;
import pk.nust.cuas.track.domain.model.TrackState;
import pk.nust.cuas.track.domain.model.TrackStatus;
import pk.nust.cuas.track.domain.model.Velocity;
import pk.nust.cuas.track.domain.port.ProtectedAssetRegistry;

class TrackOutlookCalculatorTest {

    private static final Instant T = Instant.parse("2026-08-04T12:00:00Z");
    private static final GeoPoint HERE = new GeoPoint(33.0, 73.0, 100);

    private final KalmanFilter kalman = new KalmanFilter(1.0, 25.0, 100, 100, 10);
    private final TrajectoryPredictor predictor =
            new TrajectoryPredictor(kalman, Duration.ofSeconds(30), Duration.ofSeconds(5));

    private TrackOutlookCalculator calculator(ProtectedAsset... assets) {
        ProtectedAssetRegistry registry = () -> List.of(assets);
        return new TrackOutlookCalculator(predictor, new CpaCalculator(), registry);
    }

    private Track track(TrackStatus status) {
        KalmanState filter = kalman.initial(HERE, new Velocity(20.0, 90.0));
        TrackState estimate = new TrackState(HERE, new Velocity(20.0, 90.0), T, "d1", "s1");
        return Track.reconstitute(UUID.randomUUID(), new SectorId("S"), status, filter, estimate,
                5, T.minusSeconds(20), T);
    }

    @Test
    void aConfirmedTrackGetsATrajectoryAndACpa() {
        ProtectedAsset asset = new ProtectedAsset("a1", "North Gate", new GeoPoint(33.0, 73.05, 0));

        TrackOutlook outlook = calculator(asset).outlookFor(track(TrackStatus.CONFIRMED));

        assertThat(outlook.predictedTrajectory()).hasSize(6);
        assertThat(outlook.nearestAssetCpa()).isPresent();
    }

    @Test
    void aCoastingTrackStillGetsAnOutlook() {
        // A coasting track is precisely when an operator wants to know where it is heading.
        ProtectedAsset asset = new ProtectedAsset("a1", "North Gate", new GeoPoint(33.0, 73.05, 0));

        TrackOutlook outlook = calculator(asset).outlookFor(track(TrackStatus.COASTING));

        assertThat(outlook.predictedTrajectory()).isNotEmpty();
        assertThat(outlook.nearestAssetCpa()).isPresent();
    }

    @Test
    void aTentativeTrackGetsNoOutlook() {
        // It may be a sensor artefact that never confirms; a confident trajectory and a CPA
        // countdown on screen for something that may not exist is worse than no prediction.
        ProtectedAsset asset = new ProtectedAsset("a1", "North Gate", new GeoPoint(33.0, 73.05, 0));

        TrackOutlook outlook = calculator(asset).outlookFor(track(TrackStatus.TENTATIVE));

        assertThat(outlook.predictedTrajectory()).isEmpty();
        assertThat(outlook.nearestAssetCpa()).isEmpty();
    }

    @Test
    void aLostTrackGetsNoOutlook() {
        TrackOutlook outlook = calculator(new ProtectedAsset("a1", "N", new GeoPoint(33.0, 73.05, 0)))
                .outlookFor(track(TrackStatus.LOST));

        assertThat(outlook.predictedTrajectory()).isEmpty();
        assertThat(outlook.nearestAssetCpa()).isEmpty();
    }

    @Test
    void withNoAssetsConfiguredTheTrajectoryStandsAloneAndNoCpaIsInvented() {
        TrackOutlook outlook = calculator().outlookFor(track(TrackStatus.CONFIRMED));

        assertThat(outlook.predictedTrajectory()).isNotEmpty();
        assertThat(outlook.nearestAssetCpa())
                .as("absent, not a fabricated default")
                .isEmpty();
    }
}
