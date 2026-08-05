package pk.nust.cuas.track.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
import pk.nust.cuas.track.domain.model.MergeCandidate;
import pk.nust.cuas.track.domain.model.SectorId;
import pk.nust.cuas.track.domain.model.Track;
import pk.nust.cuas.track.domain.model.TrackState;
import pk.nust.cuas.track.domain.model.TrackStatus;
import pk.nust.cuas.track.domain.model.Velocity;
import pk.nust.cuas.track.domain.port.HotTrackStateStore;
import pk.nust.cuas.track.domain.port.TrackRepository;
import pk.nust.cuas.track.domain.service.KalmanFilter;
import pk.nust.cuas.track.domain.service.TrackMergePolicy;

@ExtendWith(MockitoExtension.class)
class TrackMergeApplierTest {

    private static final Instant T = Instant.parse("2026-08-04T12:00:00Z");
    private static final GeoPoint HERE = new GeoPoint(33.0, 73.0, 100);
    private static final UUID OLDER = UUID.fromString("01900000-0000-7000-8000-000000000001");
    private static final UUID YOUNGER = UUID.fromString("01900000-0000-7000-8000-000000000002");

    @Mock
    private TrackRepository tracks;
    @Mock
    private TrackEventPublisher publisher;
    @Mock
    private HotTrackStateStore hotState;

    private final KalmanFilter kalman = new KalmanFilter(1.0, 25.0, 100, 100, 10);
    private final TrackMergePolicy policy = new TrackMergePolicy(150.0, 3);

    private TrackMergeApplier applier() {
        return new TrackMergeApplier(tracks, hotState, policy, publisher);
    }

    private Track track(UUID id, TrackStatus status) {
        KalmanState filter = kalman.initial(HERE, Velocity.NONE);
        TrackState estimate = new TrackState(HERE, Velocity.NONE, T, "d", "s");
        return Track.reconstitute(id, new SectorId("S"), status, filter, estimate, 5, T, T);
    }

    private void bothConfirmed() {
        when(tracks.findById(OLDER)).thenReturn(Optional.of(track(OLDER, TrackStatus.CONFIRMED)));
        when(tracks.findById(YOUNGER)).thenReturn(Optional.of(track(YOUNGER, TrackStatus.CONFIRMED)));
    }

    @Test
    void theFirstSweepRecordsEvidenceWithoutMerging() {
        bothConfirmed();
        when(tracks.findMergeCandidate(OLDER, YOUNGER)).thenReturn(Optional.empty());

        boolean merged = applier().advanceAndMaybeMerge(OLDER, YOUNGER, T);

        assertThat(merged).isFalse();
        ArgumentCaptor<MergeCandidate> saved = ArgumentCaptor.forClass(MergeCandidate.class);
        verify(tracks).saveMergeCandidate(saved.capture());
        assertThat(saved.getValue().consecutiveSweeps()).isEqualTo(1);
        verify(tracks, never()).merge(any(), any());
        verify(publisher, never()).publishMerged(any());
    }

    @Test
    void theSweepThatReachesTheThresholdMergesTheYoungerIntoTheOlder() {
        bothConfirmed();
        when(tracks.findMergeCandidate(OLDER, YOUNGER))
                .thenReturn(Optional.of(new MergeCandidate(OLDER, YOUNGER, 2, T, T)));

        boolean merged = applier().advanceAndMaybeMerge(OLDER, YOUNGER, T.plusSeconds(5));

        assertThat(merged).isTrue();
        ArgumentCaptor<Track> absorbed = ArgumentCaptor.forClass(Track.class);
        verify(tracks).merge(absorbed.capture(), any());
        assertThat(absorbed.getValue().id()).as("the younger is absorbed").isEqualTo(YOUNGER);
        assertThat(absorbed.getValue().status()).isEqualTo(TrackStatus.MERGED);
        assertThat(absorbed.getValue().mergedInto()).contains(OLDER);
        verify(publisher).publishMerged(any());
        verify(tracks, never()).saveMergeCandidate(any());
    }

    @Test
    void aPairThatIsNoLongerBothConfirmedLosesItsEvidenceAndDoesNotMerge() {
        // Between the sweep's snapshot and this transaction a detection or a timeout may have moved
        // one of them; the decision is made against current state, not the snapshot.
        when(tracks.findById(OLDER)).thenReturn(Optional.of(track(OLDER, TrackStatus.CONFIRMED)));
        when(tracks.findById(YOUNGER)).thenReturn(Optional.of(track(YOUNGER, TrackStatus.COASTING)));

        boolean merged = applier().advanceAndMaybeMerge(OLDER, YOUNGER, T);

        assertThat(merged).isFalse();
        verify(tracks).deleteMergeCandidate(OLDER, YOUNGER);
        verify(tracks, never()).merge(any(), any());
    }

    @Test
    void aVanishedTrackIsNotMerged() {
        when(tracks.findById(OLDER)).thenReturn(Optional.of(track(OLDER, TrackStatus.CONFIRMED)));
        when(tracks.findById(YOUNGER)).thenReturn(Optional.empty());

        assertThat(applier().advanceAndMaybeMerge(OLDER, YOUNGER, T)).isFalse();
        verify(tracks, never()).merge(any(), any());
    }

    @Test
    void discardingClearsThePairsEvidence() {
        applier().discard(OLDER, YOUNGER);

        verify(tracks).deleteMergeCandidate(OLDER, YOUNGER);
    }
}
