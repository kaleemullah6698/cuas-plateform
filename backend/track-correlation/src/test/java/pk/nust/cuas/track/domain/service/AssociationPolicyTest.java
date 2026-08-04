package pk.nust.cuas.track.domain.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import pk.nust.cuas.track.domain.model.GeoPoint;
import pk.nust.cuas.track.domain.model.SectorId;
import pk.nust.cuas.track.domain.model.Track;
import pk.nust.cuas.track.domain.model.TrackState;
import pk.nust.cuas.track.domain.model.Velocity;

class AssociationPolicyTest {

    private static final Instant T = Instant.parse("2026-08-04T12:00:00Z");
    private final AssociationPolicy policy = new AssociationPolicy(500.0, Duration.ofSeconds(30));

    private Track trackAt(double lat, double lon, Instant lastSeen) {
        TrackState state = new TrackState(new GeoPoint(lat, lon, 100), Velocity.NONE, lastSeen, "det", "s1");
        return Track.open(UUID.randomUUID(), new SectorId("SECTOR_33_73"), state);
    }

    @Test
    void selectsATrackWithinBothGates() {
        Track track = trackAt(33.6844, 73.0479, T);

        // ~90 m away, 5 s later — inside both gates.
        var match = policy.select(new GeoPoint(33.6844, 73.0489, 100), T.plusSeconds(5), List.of(track));

        assertThat(match).isPresent();
        assertThat(match.get().track()).isEqualTo(track);
        assertThat(match.get().distanceM()).isBetween(80.0, 100.0);
    }

    @Test
    void rejectsATrackOutsideTheDistanceGate() {
        Track track = trackAt(33.6844, 73.0479, T);

        // ~1.5 km away — outside the 500 m gate.
        var match = policy.select(new GeoPoint(33.6844, 73.0630, 100), T.plusSeconds(5), List.of(track));

        assertThat(match).isEmpty();
    }

    @Test
    void rejectsATrackOutsideTheTimeGate() {
        Track track = trackAt(33.6844, 73.0479, T);

        // Same place, but 45 s later — outside the 30 s gate.
        var match = policy.select(new GeoPoint(33.6844, 73.0479, 100), T.plusSeconds(45), List.of(track));

        assertThat(match).isEmpty();
    }

    @Test
    void picksTheNearestAmongSeveralCandidates() {
        Track near = trackAt(33.6844, 73.0483, T);   // ~37 m
        Track far = trackAt(33.6844, 73.0490, T);    // ~100 m

        var match = policy.select(new GeoPoint(33.6844, 73.0479, 100), T.plusSeconds(1), List.of(far, near));

        assertThat(match).isPresent();
        assertThat(match.get().track()).isEqualTo(near);
    }

    @Test
    void returnsEmptyWhenThereAreNoCandidates() {
        assertThat(policy.select(new GeoPoint(33.6844, 73.0479, 100), T, List.of())).isEmpty();
    }

    @Test
    void rejectsNonPositiveGates() {
        assertThatThrownBy(() -> new AssociationPolicy(0.0, Duration.ofSeconds(30)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new AssociationPolicy(500.0, Duration.ZERO))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
