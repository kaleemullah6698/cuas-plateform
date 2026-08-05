package pk.nust.cuas.track.application;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pk.nust.cuas.track.application.port.TrackEventPublisher;
import pk.nust.cuas.track.domain.model.GeoPoint;
import pk.nust.cuas.track.domain.model.KalmanState;
import pk.nust.cuas.track.domain.model.SectorId;
import pk.nust.cuas.track.domain.model.Track;
import pk.nust.cuas.track.domain.model.TrackClassification;
import pk.nust.cuas.track.domain.model.TrackState;
import pk.nust.cuas.track.domain.model.TrackStatus;
import pk.nust.cuas.track.domain.model.Velocity;
import pk.nust.cuas.track.domain.port.TrackRepository;
import pk.nust.cuas.track.domain.service.KalmanFilter;

@ExtendWith(MockitoExtension.class)
class TrackClassificationServiceTest {

    private static final Instant T = Instant.parse("2026-08-04T12:00:00Z");
    private static final GeoPoint HERE = new GeoPoint(33.0, 73.0, 100);

    @Mock
    private TrackRepository tracks;
    @Mock
    private TrackEventPublisher publisher;

    private final KalmanFilter kalman = new KalmanFilter(1.0, 25.0, 100, 100, 10);

    private TrackClassificationService service() {
        return new TrackClassificationService(tracks, publisher);
    }

    private Track track(TrackStatus status) {
        KalmanState filter = kalman.initial(HERE, Velocity.NONE);
        TrackState estimate = new TrackState(HERE, Velocity.NONE, T, "d1", "s1");
        return Track.reconstitute(UUID.randomUUID(), new SectorId("S"), status, filter, estimate,
                3, T, T);
    }

    private static TrackClassification classification() {
        return new TrackClassification("quadcopter-small", "DRONE", 0.94d,
                "some-service", "1.3.0", "DEVICE_CUDA", T);
    }

    @Test
    void anOpenTracksClassificationIsPublished() {
        Track track = track(TrackStatus.CONFIRMED);
        when(tracks.findById(track.id())).thenReturn(Optional.of(track));

        service().apply(track.id(), classification());

        verify(publisher).publishClassified(any(), any());
    }

    @Test
    void aClassificationForAnUnknownTrackIsDiscarded() {
        UUID unknown = UUID.randomUUID();
        when(tracks.findById(unknown)).thenReturn(Optional.empty());

        service().apply(unknown, classification());

        verify(publisher, never()).publishClassified(any(), any());
    }

    @Test
    void aClassificationForALostTrackIsDiscarded() {
        // Inference is asynchronous, so a result can outlive the track it describes. Reviving a lost
        // track because a late inference arrived would be a real defect.
        Track lost = track(TrackStatus.LOST);
        when(tracks.findById(lost.id())).thenReturn(Optional.of(lost));

        service().apply(lost.id(), classification());

        verify(publisher, never()).publishClassified(any(), any());
    }

    @Test
    void aClassificationForAMergedTrackIsDiscarded() {
        Track survivor = track(TrackStatus.CONFIRMED);
        Track merged = track(TrackStatus.CONFIRMED).mergeInto(survivor, T).track();
        when(tracks.findById(merged.id())).thenReturn(Optional.of(merged));

        service().apply(merged.id(), classification());

        verify(publisher, never()).publishClassified(any(), any());
    }

    @Test
    void aCoastingTrackIsStillClassified() {
        // A coasting track is being predicted forward, not given up on — it is exactly the kind an
        // operator wants labelled.
        Track coasting = track(TrackStatus.CONFIRMED).coast(T.plusSeconds(20), kalman).track();
        when(tracks.findById(coasting.id())).thenReturn(Optional.of(coasting));

        service().apply(coasting.id(), classification());

        verify(publisher).publishClassified(any(), any());
    }

    @Test
    void aMockedClassificationIsRecognisableAsSuch() {
        // Section 6.4: a mocked result must stay distinguishable wherever it surfaces.
        TrackClassification mocked = new TrackClassification("quadcopter-small", "DRONE", 0.94d,
                "some-service", "0.0.0-mock", "DEVICE_MOCK", T);

        org.assertj.core.api.Assertions.assertThat(mocked.isMocked()).isTrue();
        org.assertj.core.api.Assertions.assertThat(classification().isMocked()).isFalse();
    }
}
