package pk.nust.cuas.track.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pk.nust.cuas.commons.id.Uuidv7Generator;
import pk.nust.cuas.detection.v1.DetectionRecord;
import pk.nust.cuas.track.application.port.TrackClassifier;
import pk.nust.cuas.track.application.port.TrackEventPublisher;
import pk.nust.cuas.track.domain.model.GeoPoint;
import pk.nust.cuas.track.domain.model.KalmanState;
import pk.nust.cuas.track.domain.model.SectorId;
import pk.nust.cuas.track.domain.model.Track;
import pk.nust.cuas.track.domain.model.TrackLifecycleEvent;
import pk.nust.cuas.track.domain.model.TrackState;
import pk.nust.cuas.track.domain.model.TrackStatus;
import pk.nust.cuas.track.domain.model.Velocity;
import pk.nust.cuas.track.domain.port.HotTrackStateStore;
import pk.nust.cuas.track.domain.port.TrackRepository;
import pk.nust.cuas.track.domain.service.AssociationPolicy;
import pk.nust.cuas.track.domain.service.KalmanFilter;
import pk.nust.cuas.track.domain.service.SectorPolicy;
import pk.nust.cuas.track.domain.service.TrackConfirmationPolicy;
import pk.nust.cuas.track.domain.service.TrackOutlookCalculator;
import pk.nust.cuas.track.domain.service.TrackTimeoutPolicy;
import pk.nust.cuas.track.support.Detections;

@ExtendWith(MockitoExtension.class)
class DetectionCorrelationServiceTest {

    private static final Instant T = Instant.parse("2026-08-04T12:00:00Z");
    private static final double LAT = 33.6844;
    private static final double LON = 73.0479;

    @Mock
    private TrackRepository tracks;
    @Mock
    private TrackEventPublisher publisher;
    @Mock
    private TrackOutlookCalculator outlook;
    @Mock
    private HotTrackStateStore hotState;
    @Mock
    private TrackClassifier classifier;

    private final KalmanFilter kalman = new KalmanFilter(1.0, 25.0, 100, 100, 10);

    private DetectionCorrelationService service() {
        return new DetectionCorrelationService(
                tracks,
                hotState,
                new SectorPolicy(1.0),
                new AssociationPolicy(500.0),
                new TrackConfirmationPolicy(3),
                kalman,
                new TrackTimeoutPolicy(Duration.ofSeconds(10), Duration.ofSeconds(30)),
                publisher,
                outlook,
                classifier,
                new Uuidv7Generator(new Random(1)),
                Clock.fixed(T, ZoneOffset.UTC));
    }

    @Test
    void aHotStateHitAvoidsTheDatabaseEntirely() {
        // The point of the cache: the per-detection candidate query is the pipeline's hottest hop.
        Track cached = existingTrack(1, TrackStatus.TENTATIVE, T);
        when(hotState.openInSector(any())).thenReturn(Optional.of(List.of(cached)));

        service().correlate(Detections.normalized("d-hit", "s1", 2L, LAT, LON, T.plusSeconds(1)));

        verify(tracks, never()).findOpenInSector(any(), any());
        verify(publisher).publishUpdated(any(), any());
    }

    @Test
    void aHotStateMissFallsBackToTheDatabaseAndSeedsTheCache() {
        // A miss — or an unreachable Redis, which the port defines as a miss — must cost latency,
        // never correctness. Correlation continues from PostgreSQL and the cache is repopulated.
        Track existing = existingTrack(1, TrackStatus.TENTATIVE, T);
        when(hotState.openInSector(any())).thenReturn(Optional.empty());
        when(tracks.findOpenInSector(any(), any())).thenReturn(List.of(existing));

        service().correlate(Detections.normalized("d-miss", "s1", 2L, LAT, LON, T.plusSeconds(1)));

        verify(tracks).findOpenInSector(any(), any());
        verify(hotState).seed(any(), any());
        verify(publisher).publishUpdated(any(), any());
    }

    @Test
    void aTerminalTrackInTheCacheIsNeverOfferedAsACandidate() {
        // A stale entry that survived its evict must not resurrect a lost track as a candidate; the
        // detection opens a new track instead of associating to something already given up on.
        Track lost = Track.reconstitute(UUID.randomUUID(), new SectorId("SECTOR_33_73"),
                TrackStatus.LOST, kalman.initial(new GeoPoint(LAT, LON, 100), Velocity.NONE),
                new TrackState(new GeoPoint(LAT, LON, 100), Velocity.NONE, T, "d-old", "s1"), 5, T, T);
        when(hotState.openInSector(any())).thenReturn(Optional.of(List.of(lost)));

        service().correlate(Detections.normalized("d-new", "s1", 2L, LAT, LON, T.plusSeconds(1)));

        verify(publisher).publishCreated(any(), any());
        verify(publisher, never()).publishUpdated(any(), any());
    }

    private Track existingTrack(int detectionCount, TrackStatus status, Instant lastDetectionAt) {
        KalmanState filter = kalman.initial(new GeoPoint(LAT, LON, 100), Velocity.NONE);
        TrackState estimate = new TrackState(new GeoPoint(LAT, LON, 100), Velocity.NONE,
                lastDetectionAt, "d-prev", "s1");
        return Track.reconstitute(UUID.randomUUID(), new SectorId("SECTOR_33_73"), status, filter,
                estimate, detectionCount, lastDetectionAt.minusSeconds(10), lastDetectionAt);
    }

    @Test
    void opensANewTrackWhenNoCandidateMatches() {
        when(tracks.detectionAlreadyAssociated(anyString())).thenReturn(false);
        when(tracks.findOpenInSector(any(), any())).thenReturn(List.of());

        service().correlate(Detections.normalized("d1", "s1", 1L, LAT, LON, T.plusSeconds(1)));

        ArgumentCaptor<Track> created = ArgumentCaptor.forClass(Track.class);
        verify(tracks).create(created.capture(), any(), any());
        assertThat(created.getValue().status()).isEqualTo(TrackStatus.TENTATIVE);
        assertThat(created.getValue().detectionCount()).isEqualTo(1);
        verify(publisher).publishCreated(any(), any());
    }

    @Test
    void extendsANearbyTrackUsingItsPrediction() {
        when(tracks.detectionAlreadyAssociated(anyString())).thenReturn(false);
        when(tracks.findOpenInSector(any(), any()))
                .thenReturn(List.of(existingTrack(1, TrackStatus.TENTATIVE, T)));

        service().correlate(Detections.normalized("d2", "s1", 2L, LAT, LON, T.plusSeconds(3)));

        ArgumentCaptor<Track> updated = ArgumentCaptor.forClass(Track.class);
        verify(tracks).update(updated.capture(), any(), any());
        assertThat(updated.getValue().detectionCount()).isEqualTo(2);
        verify(publisher).publishUpdated(any(), any());
        verify(tracks, never()).create(any(), any(), any());
    }

    @Test
    void confirmsATrackWhenTheThresholdIsReached() {
        when(tracks.detectionAlreadyAssociated(anyString())).thenReturn(false);
        when(tracks.findOpenInSector(any(), any()))
                .thenReturn(List.of(existingTrack(2, TrackStatus.TENTATIVE, T)));

        service().correlate(Detections.normalized("d3", "s1", 3L, LAT, LON, T.plusSeconds(4)));

        ArgumentCaptor<Track> updated = ArgumentCaptor.forClass(Track.class);
        ArgumentCaptor<Optional<TrackLifecycleEvent>> event = ArgumentCaptor.forClass(Optional.class);
        verify(tracks).update(updated.capture(), any(), event.capture());
        assertThat(updated.getValue().status()).isEqualTo(TrackStatus.CONFIRMED);
        assertThat(event.getValue()).isPresent();
    }

    @Test
    void ignoresARedeliveredDetection() {
        when(tracks.detectionAlreadyAssociated("d1")).thenReturn(true);

        service().correlate(Detections.normalized("d1", "s1", 1L, LAT, LON, T));

        verify(tracks, never()).create(any(), any(), any());
        verify(tracks, never()).update(any(), any(), any());
        verifyNoInteractions(publisher);
    }

    @Test
    void skipsADetectionWithNoPosition() {
        DetectionRecord noPosition =
                Detections.normalized("d1", "s1", 1L, LAT, LON, T).toBuilder().clearPosition().build();

        service().correlate(noPosition);

        verify(tracks, never()).create(any(), any(), any());
        verify(tracks, never()).update(any(), any(), any());
        verifyNoInteractions(publisher);
    }
}
