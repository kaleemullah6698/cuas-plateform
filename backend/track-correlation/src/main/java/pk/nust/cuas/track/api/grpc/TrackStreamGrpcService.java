package pk.nust.cuas.track.api.grpc;

import io.grpc.Status;
import io.grpc.stub.ServerCallStreamObserver;
import io.grpc.stub.StreamObserver;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import pk.nust.cuas.track.application.port.TrackStreamBroadcaster;
import pk.nust.cuas.track.domain.model.Track;
import pk.nust.cuas.track.domain.model.TrackOutlook;
import pk.nust.cuas.track.common.TrackProtoMapper;
import pk.nust.cuas.track.v1.SubscribeTracksRequest;
import pk.nust.cuas.track.v1.TrackEvent;
import pk.nust.cuas.track.v1.TrackEventType;
import pk.nust.cuas.track.v1.TrackServiceGrpc;

/**
 * The live track stream, {@code TrackService.SubscribeTracks} (ARCHITECTURE.md section 4.1.5).
 *
 * <p>A server stream carrying the same {@code Track} messages the topics do, tagged with the
 * lifecycle event that produced them. It is both the gRPC adapter and the {@link
 * TrackStreamBroadcaster} the publisher fans out to — one class, because a registry with no
 * subscribers and a stream with no registry are the same object split in two.
 *
 * <p><b>A slow subscriber is dropped, never buffered without bound.</b> gRPC's {@code isReady} is
 * checked before every write and an event for a subscriber whose transport is full is discarded for
 * that subscriber only. Queueing instead would let one stalled consumer grow heap inside the service
 * that must not stop tracking drones (§5.6); dropping keeps the stream honest about being a live
 * feed with no replay, and the topics remain the durable record for anything that needs one.
 *
 * <p>Subscribers are held in a {@link CopyOnWriteArrayList}: broadcasts vastly outnumber
 * subscribe/unsubscribe, and the copy-on-write cost falls on the rare operation.
 */
@Component
public class TrackStreamGrpcService extends TrackServiceGrpc.TrackServiceImplBase
        implements TrackStreamBroadcaster {

    private static final Logger log = LoggerFactory.getLogger(TrackStreamGrpcService.class);

    private final CopyOnWriteArrayList<Subscriber> subscribers = new CopyOnWriteArrayList<>();
    private final TrackProtoMapper mapper;

    public TrackStreamGrpcService(TrackProtoMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public void subscribeTracks(SubscribeTracksRequest request, StreamObserver<TrackEvent> responses) {
        Set<String> sectors = Set.copyOf(request.getSectorIdsList());
        Subscriber subscriber = new Subscriber(
                (ServerCallStreamObserver<TrackEvent>) responses, sectors);
        subscribers.add(subscriber);
        log.debug("Track subscriber attached ({} total), sectors={}", subscribers.size(),
                sectors.isEmpty() ? "all" : sectors);

        // Both a client going away and a cancellation must remove the subscriber, or the list grows
        // for the lifetime of the process.
        ServerCallStreamObserver<TrackEvent> observer = subscriber.observer();
        observer.setOnCancelHandler(() -> remove(subscriber, "cancelled"));
        observer.setOnCloseHandler(() -> remove(subscriber, "closed"));
    }

    @Override
    public void broadcast(EventType type, Track track, TrackOutlook outlook) {
        if (subscribers.isEmpty()) {
            return;
        }
        TrackEvent event = TrackEvent.newBuilder()
                .setType(toProtoType(type))
                .setTrack(mapper.toProto(track, outlook))
                .build();
        for (Subscriber subscriber : subscribers) {
            deliver(subscriber, event, track.sector().value());
        }
    }

    /** @return how many subscribers are currently attached — for tests and health reporting */
    public int subscriberCount() {
        return subscribers.size();
    }

    private void deliver(Subscriber subscriber, TrackEvent event, String sector) {
        if (!subscriber.wants(sector)) {
            return;
        }
        if (!subscriber.observer().isReady()) {
            // Dropped for this subscriber alone. See the class note: buffering would let one stalled
            // consumer grow heap inside the correlator.
            log.debug("Dropping {} for a subscriber whose transport is not ready", event.getType());
            return;
        }
        try {
            subscriber.observer().onNext(event);
        } catch (RuntimeException ex) {
            remove(subscriber, "write failed");
            try {
                subscriber.observer().onError(Status.UNAVAILABLE
                        .withDescription("track stream failed").asRuntimeException());
            } catch (RuntimeException ignored) {
                // The transport is already gone; there is nothing to tell the client on.
            }
        }
    }

    private void remove(Subscriber subscriber, String reason) {
        if (subscriber.detached().compareAndSet(false, true) && subscribers.remove(subscriber)) {
            log.debug("Track subscriber detached ({}); {} remain", reason, subscribers.size());
        }
    }

    private static TrackEventType toProtoType(EventType type) {
        return switch (type) {
            case CREATED -> TrackEventType.TRACK_EVENT_TYPE_CREATED;
            case UPDATED -> TrackEventType.TRACK_EVENT_TYPE_UPDATED;
            case LOST -> TrackEventType.TRACK_EVENT_TYPE_LOST;
            case MERGED -> TrackEventType.TRACK_EVENT_TYPE_MERGED;
        };
    }

    /**
     * One attached client and the sectors it asked for.
     *
     * @param observer the stream to write to
     * @param sectors  the sectors it wants; empty means every sector
     * @param detached guards against removing the same subscriber twice, since cancel and close can
     *                 both fire
     */
    private record Subscriber(ServerCallStreamObserver<TrackEvent> observer, Set<String> sectors,
                              AtomicBoolean detached) {

        Subscriber(ServerCallStreamObserver<TrackEvent> observer, Set<String> sectors) {
            this(observer, sectors, new AtomicBoolean(false));
        }

        boolean wants(String sector) {
            return sectors.isEmpty() || sectors.contains(sector);
        }
    }
}
