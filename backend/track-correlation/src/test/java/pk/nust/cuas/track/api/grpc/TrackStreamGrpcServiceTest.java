package pk.nust.cuas.track.api.grpc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.grpc.stub.ServerCallStreamObserver;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import pk.nust.cuas.track.application.port.TrackStreamBroadcaster.EventType;
import pk.nust.cuas.track.common.TrackProtoMapper;
import pk.nust.cuas.track.domain.model.GeoPoint;
import pk.nust.cuas.track.domain.model.KalmanState;
import pk.nust.cuas.track.domain.model.SectorId;
import pk.nust.cuas.track.domain.model.Track;
import pk.nust.cuas.track.domain.model.TrackOutlook;
import pk.nust.cuas.track.domain.model.TrackState;
import pk.nust.cuas.track.domain.model.TrackStatus;
import pk.nust.cuas.track.domain.model.Velocity;
import pk.nust.cuas.track.domain.service.KalmanFilter;
import pk.nust.cuas.track.v1.SubscribeTracksRequest;
import pk.nust.cuas.track.v1.TrackEvent;
import pk.nust.cuas.track.v1.TrackEventType;

class TrackStreamGrpcServiceTest {

    private static final Instant T = Instant.parse("2026-08-04T12:00:00Z");
    private static final GeoPoint HERE = new GeoPoint(33.0, 73.0, 100);

    private final KalmanFilter kalman = new KalmanFilter(1.0, 25.0, 100, 100, 10);
    private final TrackStreamGrpcService service = new TrackStreamGrpcService(new TrackProtoMapper(kalman));

    private Track track(String sector) {
        KalmanState filter = kalman.initial(HERE, Velocity.NONE);
        TrackState estimate = new TrackState(HERE, Velocity.NONE, T, "d1", "s1");
        return Track.reconstitute(UUID.randomUUID(), new SectorId(sector), TrackStatus.CONFIRMED,
                filter, estimate, 3, T, T);
    }

    /** A ready observer that records everything written to it. */
    @SuppressWarnings("unchecked")
    private static ServerCallStreamObserver<TrackEvent> readyObserver(List<TrackEvent> sink) {
        ServerCallStreamObserver<TrackEvent> observer = mock(ServerCallStreamObserver.class);
        when(observer.isReady()).thenReturn(true);
        org.mockito.Mockito.doAnswer(invocation -> {
            sink.add(invocation.getArgument(0));
            return null;
        }).when(observer).onNext(any());
        return observer;
    }

    private void subscribe(ServerCallStreamObserver<TrackEvent> observer, String... sectors) {
        service.subscribeTracks(
                SubscribeTracksRequest.newBuilder().addAllSectorIds(List.of(sectors)).build(), observer);
    }

    @Test
    void aSubscriberReceivesEveryEventTypeWithTheMatchingTag() {
        List<TrackEvent> received = new ArrayList<>();
        subscribe(readyObserver(received));

        Track track = track("SECTOR_A");
        service.broadcast(EventType.CREATED, track, TrackOutlook.none());
        service.broadcast(EventType.UPDATED, track, TrackOutlook.none());
        service.broadcast(EventType.LOST, track, TrackOutlook.none());
        service.broadcast(EventType.MERGED, track, TrackOutlook.none());

        assertThat(received).extracting(TrackEvent::getType).containsExactly(
                TrackEventType.TRACK_EVENT_TYPE_CREATED,
                TrackEventType.TRACK_EVENT_TYPE_UPDATED,
                TrackEventType.TRACK_EVENT_TYPE_LOST,
                TrackEventType.TRACK_EVENT_TYPE_MERGED);
        assertThat(received.get(0).getTrack().getTrackId()).isEqualTo(track.id().toString());
    }

    @Test
    void anEmptySectorFilterMeansEverySector() {
        List<TrackEvent> received = new ArrayList<>();
        subscribe(readyObserver(received));

        service.broadcast(EventType.UPDATED, track("SECTOR_A"), TrackOutlook.none());
        service.broadcast(EventType.UPDATED, track("SECTOR_B"), TrackOutlook.none());

        assertThat(received).hasSize(2);
    }

    @Test
    void aSectorFilterExcludesOtherSectors() {
        List<TrackEvent> received = new ArrayList<>();
        subscribe(readyObserver(received), "SECTOR_A");

        service.broadcast(EventType.UPDATED, track("SECTOR_A"), TrackOutlook.none());
        service.broadcast(EventType.UPDATED, track("SECTOR_B"), TrackOutlook.none());

        assertThat(received).hasSize(1);
        assertThat(received.get(0).getTrack().getSectorId()).isEqualTo("SECTOR_A");
    }

    @Test
    @SuppressWarnings("unchecked")
    void anEventForASubscriberWhoseTransportIsFullIsDroppedNotBuffered() {
        // Queueing would let one stalled consumer grow heap inside the service that must not stop
        // tracking drones; the stream is a live feed and the topics are the durable record.
        ServerCallStreamObserver<TrackEvent> stalled = mock(ServerCallStreamObserver.class);
        when(stalled.isReady()).thenReturn(false);
        subscribe(stalled);

        service.broadcast(EventType.UPDATED, track("SECTOR_A"), TrackOutlook.none());

        verify(stalled, never()).onNext(any());
        assertThat(service.subscriberCount()).as("dropping an event does not drop the subscriber").isEqualTo(1);
    }

    @Test
    void oneStalledSubscriberDoesNotStarveTheOthers() {
        List<TrackEvent> received = new ArrayList<>();
        @SuppressWarnings("unchecked")
        ServerCallStreamObserver<TrackEvent> stalled = mock(ServerCallStreamObserver.class);
        when(stalled.isReady()).thenReturn(false);
        subscribe(stalled);
        subscribe(readyObserver(received));

        service.broadcast(EventType.UPDATED, track("SECTOR_A"), TrackOutlook.none());

        assertThat(received).hasSize(1);
    }

    @Test
    @SuppressWarnings("unchecked")
    void aSubscriberWhoseWriteFailsIsRemoved() {
        ServerCallStreamObserver<TrackEvent> broken = mock(ServerCallStreamObserver.class);
        when(broken.isReady()).thenReturn(true);
        doThrow(new IllegalStateException("transport gone")).when(broken).onNext(any());
        subscribe(broken);

        service.broadcast(EventType.UPDATED, track("SECTOR_A"), TrackOutlook.none());

        assertThat(service.subscriberCount()).isZero();
    }

    @Test
    void broadcastingWithNoSubscribersIsHarmless() {
        service.broadcast(EventType.UPDATED, track("SECTOR_A"), TrackOutlook.none());

        assertThat(service.subscriberCount()).isZero();
    }

    @Test
    @SuppressWarnings("unchecked")
    void aCancelledSubscriberIsRemovedExactlyOnce() {
        // Cancel and close can both fire; removing twice would corrupt the count.
        ServerCallStreamObserver<TrackEvent> observer = mock(ServerCallStreamObserver.class);
        List<Runnable> handlers = new ArrayList<>();
        org.mockito.Mockito.doAnswer(inv -> handlers.add(inv.getArgument(0)))
                .when(observer).setOnCancelHandler(any());
        org.mockito.Mockito.doAnswer(inv -> handlers.add(inv.getArgument(0)))
                .when(observer).setOnCloseHandler(any());
        subscribe(observer);
        assertThat(service.subscriberCount()).isEqualTo(1);

        handlers.forEach(Runnable::run);

        assertThat(service.subscriberCount()).isZero();
    }
}
