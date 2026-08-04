package pk.nust.cuas.track.domain.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import pk.nust.cuas.track.domain.service.TrackConfirmationPolicy;

class TrackTest {

    private static final Instant T = Instant.parse("2026-08-04T12:00:00Z");
    private final TrackConfirmationPolicy confirmAt3 = new TrackConfirmationPolicy(3);

    private TrackState stateAt(double lat, double lon, Instant at, String detectionId) {
        return new TrackState(new GeoPoint(lat, lon, 100), new Velocity(10, 90), at, detectionId, "s1");
    }

    @Test
    void aNewTrackIsTentativeWithOneDetection() {
        Track track = Track.open(UUID.randomUUID(), new SectorId("S"), stateAt(33.0, 73.0, T, "d1"));

        assertThat(track.status()).isEqualTo(TrackStatus.TENTATIVE);
        assertThat(track.detectionCount()).isEqualTo(1);
        assertThat(track.firstSeen()).isEqualTo(T);
        assertThat(track.lastSeen()).isEqualTo(T);
    }

    @Test
    void associatingAdvancesTheStateAndCountButKeepsFirstSeen() {
        Track track = Track.open(UUID.randomUUID(), new SectorId("S"), stateAt(33.0, 73.0, T, "d1"));

        TrackState next = stateAt(33.001, 73.001, T.plusSeconds(5), "d2");
        Track.AssociationOutcome outcome = track.associate(next, confirmAt3);

        assertThat(outcome.track().detectionCount()).isEqualTo(2);
        assertThat(outcome.track().status()).isEqualTo(TrackStatus.TENTATIVE);
        assertThat(outcome.track().state()).isEqualTo(next);
        assertThat(outcome.track().lastSeen()).isEqualTo(T.plusSeconds(5));
        assertThat(outcome.track().firstSeen()).isEqualTo(T);
        assertThat(outcome.lifecycleEvent()).isEmpty();
    }

    @Test
    void aTrackConfirmsOnReachingTheThresholdAndEmitsALifecycleEvent() {
        Track track = Track.open(UUID.randomUUID(), new SectorId("S"), stateAt(33.0, 73.0, T, "d1"));
        track = track.associate(stateAt(33.0, 73.0, T.plusSeconds(2), "d2"), confirmAt3).track();

        Track.AssociationOutcome third = track.associate(stateAt(33.0, 73.0, T.plusSeconds(4), "d3"), confirmAt3);

        assertThat(third.track().status()).isEqualTo(TrackStatus.CONFIRMED);
        assertThat(third.track().detectionCount()).isEqualTo(3);
        assertThat(third.lifecycleEvent()).hasValueSatisfying(event -> {
            assertThat(event.from()).contains(TrackStatus.TENTATIVE);
            assertThat(event.to()).isEqualTo(TrackStatus.CONFIRMED);
        });
    }

    @Test
    void aConfirmedTrackStaysConfirmedWithoutFurtherLifecycleEvents() {
        Track track = Track.open(UUID.randomUUID(), new SectorId("S"), stateAt(33.0, 73.0, T, "d1"));
        track = track.associate(stateAt(33.0, 73.0, T.plusSeconds(2), "d2"), confirmAt3).track();
        track = track.associate(stateAt(33.0, 73.0, T.plusSeconds(4), "d3"), confirmAt3).track();

        Track.AssociationOutcome fourth = track.associate(stateAt(33.0, 73.0, T.plusSeconds(6), "d4"), confirmAt3);

        assertThat(fourth.track().status()).isEqualTo(TrackStatus.CONFIRMED);
        assertThat(fourth.track().detectionCount()).isEqualTo(4);
        assertThat(fourth.lifecycleEvent()).isEmpty();
    }
}
