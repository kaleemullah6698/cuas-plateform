package pk.nust.cuas.track.domain.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import pk.nust.cuas.track.domain.model.GeoPoint;
import pk.nust.cuas.track.domain.model.KalmanState;
import pk.nust.cuas.track.domain.model.SectorId;
import pk.nust.cuas.track.domain.model.Track;
import pk.nust.cuas.track.domain.model.TrackState;
import pk.nust.cuas.track.domain.model.Velocity;
import pk.nust.cuas.track.domain.service.AssociationPolicy.Candidate;

class AssociationPolicyTest {

    private static final GeoPoint DETECTION = new GeoPoint(33.6844, 73.0479, 100);
    private final AssociationPolicy policy = new AssociationPolicy(500.0);
    private final KalmanFilter kalman = new KalmanFilter(1.0, 25.0, 100, 100, 10);

    /** A track whose identity is all the policy uses; the predicted position is supplied per case. */
    private Track anyTrack() {
        KalmanState filter = kalman.initial(DETECTION, Velocity.NONE);
        TrackState estimate = new TrackState(DETECTION, Velocity.NONE, Instant.now(), "d", "s1");
        return Track.open(UUID.randomUUID(), new SectorId("SECTOR_33_73"), filter, estimate);
    }

    @Test
    void selectsATrackWhosePredictionIsWithinTheGate() {
        Track track = anyTrack();
        // ~90 m from the detection.
        Candidate candidate = new Candidate(track, new GeoPoint(33.6844, 73.0489, 100));

        var match = policy.select(DETECTION, List.of(candidate));

        assertThat(match).isPresent();
        assertThat(match.get().track()).isEqualTo(track);
        assertThat(match.get().distanceM()).isBetween(80.0, 100.0);
    }

    @Test
    void rejectsATrackWhosePredictionIsOutsideTheGate() {
        // ~1.5 km from the detection.
        Candidate candidate = new Candidate(anyTrack(), new GeoPoint(33.6844, 73.0630, 100));

        assertThat(policy.select(DETECTION, List.of(candidate))).isEmpty();
    }

    @Test
    void picksTheNearestPredictionAmongSeveral() {
        Track near = anyTrack();
        Candidate nearCandidate = new Candidate(near, new GeoPoint(33.6844, 73.0483, 100)); // ~37 m
        Candidate farCandidate = new Candidate(anyTrack(), new GeoPoint(33.6844, 73.0490, 100)); // ~100 m

        var match = policy.select(DETECTION, List.of(farCandidate, nearCandidate));

        assertThat(match).isPresent();
        assertThat(match.get().track()).isEqualTo(near);
    }

    @Test
    void returnsEmptyWhenThereAreNoCandidates() {
        assertThat(policy.select(DETECTION, List.of())).isEmpty();
    }

    @Test
    void rejectsANonPositiveGate() {
        assertThatThrownBy(() -> new AssociationPolicy(0.0)).isInstanceOf(IllegalArgumentException.class);
    }
}
