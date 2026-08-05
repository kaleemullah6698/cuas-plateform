package pk.nust.cuas.track.domain.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import pk.nust.cuas.track.domain.model.GeoPoint;
import pk.nust.cuas.track.domain.model.KalmanState;
import pk.nust.cuas.track.domain.model.SectorId;
import pk.nust.cuas.track.domain.model.Track;
import pk.nust.cuas.track.domain.model.TrackState;
import pk.nust.cuas.track.domain.model.TrackStatus;
import pk.nust.cuas.track.domain.model.Velocity;

class TrackMergePolicyTest {

    private static final Instant T = Instant.parse("2026-08-04T12:00:00Z");
    private static final double EARTH_RADIUS_M = 6_371_000.0d;
    private static final GeoPoint HERE = new GeoPoint(33.0, 73.0, 100);

    private final KalmanFilter kalman = new KalmanFilter(1.0, 25.0, 100, 100, 10);
    private final TrackMergePolicy policy = new TrackMergePolicy(150.0, 3);

    private static GeoPoint east(double metres) {
        double degrees = Math.toDegrees(metres / (EARTH_RADIUS_M * Math.cos(Math.toRadians(HERE.latitude()))));
        return new GeoPoint(HERE.latitude(), HERE.longitude() + degrees, HERE.altitudeM());
    }

    private Track track(UUID id, TrackStatus status, String sector, GeoPoint at) {
        KalmanState filter = kalman.initial(at, Velocity.NONE);
        TrackState estimate = new TrackState(at, Velocity.NONE, T, "d", "s");
        return Track.reconstitute(id, new SectorId(sector), status, filter, estimate, 5, T, T);
    }

    /** Two ids in a known order, so "older" is unambiguous in the survivor tests. */
    private static final UUID OLDER = UUID.fromString("01900000-0000-7000-8000-000000000001");
    private static final UUID YOUNGER = UUID.fromString("01900000-0000-7000-8000-000000000002");

    @Test
    void twoConfirmedTracksCloseTogetherFromDifferentSensorsAreCandidates() {
        Track a = track(OLDER, TrackStatus.CONFIRMED, "S", HERE);
        Track b = track(YOUNGER, TrackStatus.CONFIRMED, "S", east(50));

        assertThat(policy.areCandidates(a, Set.of("radar-1"), b, Set.of("rf-1"))).isTrue();
    }

    @Test
    void tracksSharingASensorAreNotCandidates() {
        // One sensor reporting the same object twice is a duplicate detection, which association
        // already handles. Two tracks sharing a sensor are far more likely to be two real objects
        // that sensor is resolving separately.
        Track a = track(OLDER, TrackStatus.CONFIRMED, "S", HERE);
        Track b = track(YOUNGER, TrackStatus.CONFIRMED, "S", east(50));

        assertThat(policy.areCandidates(a, Set.of("radar-1", "rf-1"), b, Set.of("rf-1"))).isFalse();
    }

    @Test
    void tracksOutsideTheGateAreNotCandidates() {
        Track a = track(OLDER, TrackStatus.CONFIRMED, "S", HERE);
        Track b = track(YOUNGER, TrackStatus.CONFIRMED, "S", east(500));

        assertThat(policy.areCandidates(a, Set.of("radar-1"), b, Set.of("rf-1"))).isFalse();
    }

    @Test
    void tracksInDifferentSectorsAreNotCandidates() {
        Track a = track(OLDER, TrackStatus.CONFIRMED, "SECTOR_A", HERE);
        Track b = track(YOUNGER, TrackStatus.CONFIRMED, "SECTOR_B", east(50));

        assertThat(policy.areCandidates(a, Set.of("radar-1"), b, Set.of("rf-1"))).isFalse();
    }

    @Test
    void aTentativeTrackIsNeverACandidate() {
        Track a = track(OLDER, TrackStatus.TENTATIVE, "S", HERE);
        Track b = track(YOUNGER, TrackStatus.CONFIRMED, "S", east(50));

        assertThat(policy.areCandidates(a, Set.of("radar-1"), b, Set.of("rf-1"))).isFalse();
    }

    @Test
    void aCoastingTrackIsNeverACandidate() {
        // Its position is predicted rather than observed; merging on a prediction would let two
        // tracks collapse because neither is being measured.
        Track a = track(OLDER, TrackStatus.COASTING, "S", HERE);
        Track b = track(YOUNGER, TrackStatus.CONFIRMED, "S", east(50));

        assertThat(policy.areCandidates(a, Set.of("radar-1"), b, Set.of("rf-1"))).isFalse();
    }

    @Test
    void aTrackIsNeverACandidateWithItself() {
        Track a = track(OLDER, TrackStatus.CONFIRMED, "S", HERE);

        assertThat(policy.areCandidates(a, Set.of("radar-1"), a, Set.of("rf-1"))).isFalse();
    }

    @Test
    void oneSweepInsideTheGateIsNotEnough() {
        // A single coincidence is exactly what two drones in close formation produce.
        assertThat(policy.shouldMerge(1)).isFalse();
        assertThat(policy.shouldMerge(2)).isFalse();
        assertThat(policy.shouldMerge(3)).isTrue();
        assertThat(policy.shouldMerge(4)).isTrue();
    }

    @Test
    void theOlderTrackSurvives_whicheverOrderThePairIsGivenIn() {
        Track older = track(OLDER, TrackStatus.CONFIRMED, "S", HERE);
        Track younger = track(YOUNGER, TrackStatus.CONFIRMED, "S", east(50));

        assertThat(policy.survivorOf(older, younger).id()).isEqualTo(OLDER);
        assertThat(policy.survivorOf(younger, older).id())
                .as("the decision must not depend on argument order")
                .isEqualTo(OLDER);
    }

    @Test
    void aNonPositiveGateOrSweepCountIsRejected() {
        assertThatThrownBy(() -> new TrackMergePolicy(0.0, 3)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new TrackMergePolicy(150.0, 0)).isInstanceOf(IllegalArgumentException.class);
    }
}
