package pk.nust.cuas.track.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pk.nust.cuas.track.application.port.TrackEventPublisher;
import pk.nust.cuas.track.domain.model.GeoPoint;
import pk.nust.cuas.track.domain.model.KalmanState;
import pk.nust.cuas.track.domain.model.SectorId;
import pk.nust.cuas.track.domain.model.Track;
import pk.nust.cuas.track.domain.model.TrackState;
import pk.nust.cuas.track.domain.model.TrackStatus;
import pk.nust.cuas.track.domain.model.Velocity;
import pk.nust.cuas.track.domain.port.TrackRepository;
import pk.nust.cuas.track.domain.service.KalmanFilter;
import pk.nust.cuas.track.domain.service.TrackTimeoutPolicy;

@ExtendWith(MockitoExtension.class)
class TrackTimeoutApplierTest {

    private static final Instant LAST_DETECTION = Instant.parse("2026-08-04T12:00:00Z");

    @Mock
    private TrackRepository tracks;
    @Mock
    private TrackEventPublisher publisher;

    private final KalmanFilter kalman = new KalmanFilter(1.0, 25.0, 100, 100, 10);

    private TrackTimeoutApplier applier() {
        return new TrackTimeoutApplier(tracks,
                new TrackTimeoutPolicy(Duration.ofSeconds(10), Duration.ofSeconds(30)), kalman, publisher);
    }

    private Track track(TrackStatus status) {
        KalmanState filter = kalman.initial(new GeoPoint(33.0, 73.0, 100), new Velocity(10, 0));
        TrackState estimate = new TrackState(new GeoPoint(33.0, 73.0, 100), new Velocity(10, 0),
                LAST_DETECTION, "d", "s1");
        return Track.reconstitute(UUID.randomUUID(), new SectorId("S"), status, filter, estimate, 5,
                LAST_DETECTION, LAST_DETECTION);
    }

    @Test
    void coastsAConfirmedTrackPastTheCoastTimeout() {
        Track confirmed = track(TrackStatus.CONFIRMED);
        when(tracks.findById(confirmed.id())).thenReturn(Optional.of(confirmed));

        applier().apply(confirmed.id(), LAST_DETECTION.plusSeconds(15));

        ArgumentCaptor<Track> coasted = ArgumentCaptor.forClass(Track.class);
        verify(tracks).coast(coasted.capture(), any());
        assertThat(coasted.getValue().status()).isEqualTo(TrackStatus.COASTING);
        verify(publisher).publishUpdated(any());
        verify(tracks, never()).lose(any(), any());
    }

    @Test
    void losesAnOpenTrackPastTheLostTimeout() {
        Track coasting = track(TrackStatus.COASTING);
        when(tracks.findById(coasting.id())).thenReturn(Optional.of(coasting));

        applier().apply(coasting.id(), LAST_DETECTION.plusSeconds(40));

        ArgumentCaptor<Track> lost = ArgumentCaptor.forClass(Track.class);
        verify(tracks).lose(lost.capture(), any());
        assertThat(lost.getValue().status()).isEqualTo(TrackStatus.LOST);
        verify(publisher).publishLost(any());
    }

    @Test
    void doesNothingForAFreshTrack() {
        Track confirmed = track(TrackStatus.CONFIRMED);
        when(tracks.findById(confirmed.id())).thenReturn(Optional.of(confirmed));

        applier().apply(confirmed.id(), LAST_DETECTION.plusSeconds(5));

        verify(tracks, never()).coast(any(), any());
        verify(tracks, never()).lose(any(), any());
        verify(publisher, never()).publishUpdated(any());
        verify(publisher, never()).publishLost(any());
    }

    @Test
    void doesNothingWhenTheTrackHasGone() {
        UUID id = UUID.randomUUID();
        when(tracks.findById(id)).thenReturn(Optional.empty());

        applier().apply(id, LAST_DETECTION.plusSeconds(40));

        verify(tracks, never()).coast(any(), any());
        verify(tracks, never()).lose(any(), any());
    }
}
