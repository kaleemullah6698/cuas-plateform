package pk.nust.cuas.track.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import pk.nust.cuas.track.domain.service.KalmanFilter;
import pk.nust.cuas.track.domain.service.TrackConfirmationPolicy;

class TrackTest {

    private static final Instant T = Instant.parse("2026-08-04T12:00:00Z");
    private static final GeoPoint ORIGIN = new GeoPoint(33.0, 73.0, 100);

    private final KalmanFilter kalman = new KalmanFilter(1.0, 25.0, 100, 100, 10);
    private final TrackConfirmationPolicy confirmAt3 = new TrackConfirmationPolicy(3);

    private Track openTrack() {
        KalmanState filter = kalman.initial(ORIGIN, Velocity.NONE);
        TrackState estimate = new TrackState(kalman.position(filter), kalman.velocity(filter), T, "d1", "s1");
        return Track.open(UUID.randomUUID(), new SectorId("S"), filter, estimate);
    }

    @Test
    void aNewTrackIsTentativeWithOneDetection() {
        Track track = openTrack();

        assertThat(track.status()).isEqualTo(TrackStatus.TENTATIVE);
        assertThat(track.detectionCount()).isEqualTo(1);
        assertThat(track.firstSeen()).isEqualTo(T);
        assertThat(track.lastDetectionAt()).isEqualTo(T);
    }

    @Test
    void associatingAdvancesTheEstimateAndCountButKeepsFirstSeen() {
        Track.Transition outcome = openTrack()
                .associate(new GeoPoint(33.001, 73.0, 100), T.plusSeconds(5), "d2", "s1", kalman, confirmAt3);

        assertThat(outcome.track().detectionCount()).isEqualTo(2);
        assertThat(outcome.track().status()).isEqualTo(TrackStatus.TENTATIVE);
        assertThat(outcome.track().lastDetectionAt()).isEqualTo(T.plusSeconds(5));
        assertThat(outcome.track().firstSeen()).isEqualTo(T);
        assertThat(outcome.track().estimate().observedAt()).isEqualTo(T.plusSeconds(5));
        assertThat(outcome.lifecycleEvent()).isEmpty();
    }

    @Test
    void aTrackConfirmsOnReachingTheThreshold() {
        Track track = openTrack();
        track = track.associate(new GeoPoint(33.0, 73.0, 100), T.plusSeconds(2), "d2", "s1", kalman, confirmAt3).track();

        Track.Transition third = track.associate(new GeoPoint(33.0, 73.0, 100), T.plusSeconds(4), "d3", "s1", kalman, confirmAt3);

        assertThat(third.track().status()).isEqualTo(TrackStatus.CONFIRMED);
        assertThat(third.track().detectionCount()).isEqualTo(3);
        assertThat(third.lifecycleEvent()).hasValueSatisfying(event -> {
            assertThat(event.from()).contains(TrackStatus.TENTATIVE);
            assertThat(event.to()).isEqualTo(TrackStatus.CONFIRMED);
        });
    }

    @Test
    void aConfirmedTrackCoastsWithAPredictedEstimate() {
        Track confirmed = confirmedTrack();

        Track.Transition coasting = confirmed.coast(T.plusSeconds(20), kalman);

        assertThat(coasting.track().status()).isEqualTo(TrackStatus.COASTING);
        // The estimate is predicted forward to the coast time; the last-detection time does not move.
        assertThat(coasting.track().estimate().observedAt()).isEqualTo(T.plusSeconds(20));
        assertThat(coasting.track().lastDetectionAt()).isEqualTo(confirmed.lastDetectionAt());
        assertThat(coasting.lifecycleEvent()).hasValueSatisfying(event ->
                assertThat(event.to()).isEqualTo(TrackStatus.COASTING));
    }

    @Test
    void onlyAConfirmedTrackCanCoast() {
        assertThatThrownBy(() -> openTrack().coast(T.plusSeconds(20), kalman))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void aCoastingTrackRevivesToConfirmedWhenReAssociated() {
        Track coasting = confirmedTrack().coast(T.plusSeconds(15), kalman).track();

        Track.Transition revived = coasting.associate(
                new GeoPoint(33.0, 73.0, 100), T.plusSeconds(18), "d9", "s1", kalman, confirmAt3);

        assertThat(revived.track().status()).isEqualTo(TrackStatus.CONFIRMED);
        assertThat(revived.lifecycleEvent()).hasValueSatisfying(event -> {
            assertThat(event.from()).contains(TrackStatus.COASTING);
            assertThat(event.to()).isEqualTo(TrackStatus.CONFIRMED);
        });
    }

    @Test
    void revivingACoastingTrackAdvancesTheFilterOnlyFromWhereCoastingLeftIt() {
        // A coasting track's filter has already been predicted forward to the coast time, and its
        // estimate records that time; only lastDetectionAt stays behind, because the miss timeouts
        // count from the last detection. Associating must therefore advance the filter from the
        // estimate's time, not from lastDetectionAt — measuring the gap from the latter would
        // re-advance the filter over an interval it has already covered.
        Track moving = movingTrack();
        Track coasting = moving.coast(T.plusSeconds(20), kalman).track();
        GeoPoint coastedTo = coasting.estimate().position();

        // A measurement at exactly the coast time, exactly where the track was predicted to be: a
        // zero-length step onto a zero innovation, so the estimate must not move at all.
        Track.Transition revived = coasting.associate(
                coastedTo, T.plusSeconds(20), "d9", "s1", kalman, confirmAt3);

        assertThat(revived.track().estimate().position().distanceTo(coastedTo)).isLessThan(0.001d);
        assertThat(revived.track().estimate().velocity().speedMs())
                .isCloseTo(coasting.estimate().velocity().speedMs(), within(0.001d));
        assertThat(revived.track().estimate().observedAt()).isEqualTo(T.plusSeconds(20));
        assertThat(revived.track().lastDetectionAt()).isEqualTo(T.plusSeconds(20));
    }

    @Test
    void anOpenTrackCanBeMarkedLostAndIsThenTerminal() {
        Track.Transition lost = confirmedTrack().markLost(T.plusSeconds(40), "no detection within the lost timeout");

        assertThat(lost.track().status()).isEqualTo(TrackStatus.LOST);
        assertThat(lost.lifecycleEvent()).hasValueSatisfying(event ->
                assertThat(event.to()).isEqualTo(TrackStatus.LOST));
        assertThatThrownBy(() -> lost.track().markLost(T.plusSeconds(50), "again"))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void aLostTrackRejectsFurtherAssociation() {
        Track lost = confirmedTrack().markLost(T.plusSeconds(40), "lost").track();

        assertThatThrownBy(() -> lost.associate(
                new GeoPoint(33.0, 73.0, 100), T.plusSeconds(45), "d9", "s1", kalman, confirmAt3))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void mergingAbsorbsATrackIntoAnotherAndIsTerminal() {
        Track survivor = confirmedTrack();
        Track absorbed = confirmedTrack();

        Track.Transition merged = absorbed.mergeInto(survivor, T.plusSeconds(30));

        assertThat(merged.track().status()).isEqualTo(TrackStatus.MERGED);
        assertThat(merged.track().mergedInto()).contains(survivor.id());
        assertThat(merged.track().status().isOpen()).isFalse();
        assertThat(merged.lifecycleEvent()).hasValueSatisfying(event -> {
            assertThat(event.from()).contains(TrackStatus.CONFIRMED);
            assertThat(event.to()).isEqualTo(TrackStatus.MERGED);
        });
    }

    @Test
    void aMergedTrackKeepsItsOwnFinalEstimate() {
        // Re-running the filter over the survivor's detections would reprocess history to produce a
        // state the survivor already holds a better version of. The merged track keeps its own
        // estimate as the record of what it believed, and points at the survivor for the live answer.
        Track survivor = confirmedTrack();
        Track absorbed = confirmedTrack();

        Track merged = absorbed.mergeInto(survivor, T.plusSeconds(30)).track();

        assertThat(merged.estimate()).isEqualTo(absorbed.estimate());
        assertThat(merged.detectionCount()).isEqualTo(absorbed.detectionCount());
    }

    @Test
    void aMergedTrackRejectsFurtherAssociationAndFurtherMerging() {
        Track survivor = confirmedTrack();
        Track merged = confirmedTrack().mergeInto(survivor, T.plusSeconds(30)).track();

        assertThatThrownBy(() -> merged.associate(
                new GeoPoint(33.0, 73.0, 100), T.plusSeconds(35), "d9", "s1", kalman, confirmAt3))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> merged.mergeInto(survivor, T.plusSeconds(40)))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void aTrackCannotBeMergedIntoItself() {
        Track track = confirmedTrack();

        assertThatThrownBy(() -> track.mergeInto(track, T.plusSeconds(30)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void aLostTrackCannotBeMerged() {
        Track lost = confirmedTrack().markLost(T.plusSeconds(40), "lost").track();

        assertThatThrownBy(() -> lost.mergeInto(confirmedTrack(), T.plusSeconds(45)))
                .isInstanceOf(IllegalStateException.class);
    }

    /** A confirmed track with real eastward motion, so a mis-timed prediction visibly displaces it. */
    private Track movingTrack() {
        KalmanState filter = kalman.initial(ORIGIN, new Velocity(30.0d, 90.0d));
        TrackState estimate = new TrackState(kalman.position(filter), kalman.velocity(filter), T, "m1", "s1");
        Track track = Track.open(UUID.randomUUID(), new SectorId("S"), filter, estimate);
        // Two further fixes 30 m/s east, so the filter settles on a genuine eastward velocity.
        track = track.associate(east(ORIGIN, 60.0d), T.plusSeconds(2), "m2", "s1", kalman, confirmAt3).track();
        return track.associate(east(ORIGIN, 120.0d), T.plusSeconds(4), "m3", "s1", kalman, confirmAt3).track();
    }

    private static GeoPoint east(GeoPoint from, double metres) {
        double degrees = Math.toDegrees(metres / (6_371_000.0d * Math.cos(Math.toRadians(from.latitude()))));
        return new GeoPoint(from.latitude(), from.longitude() + degrees, from.altitudeM());
    }

    private Track confirmedTrack() {
        Track track = openTrack();
        track = track.associate(new GeoPoint(33.0, 73.0, 100), T.plusSeconds(2), "d2", "s1", kalman, confirmAt3).track();
        return track.associate(new GeoPoint(33.0, 73.0, 100), T.plusSeconds(4), "d3", "s1", kalman, confirmAt3).track();
    }
}
