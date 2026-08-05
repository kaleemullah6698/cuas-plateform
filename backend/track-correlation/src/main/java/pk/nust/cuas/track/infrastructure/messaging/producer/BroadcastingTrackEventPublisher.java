package pk.nust.cuas.track.infrastructure.messaging.producer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;
import pk.nust.cuas.track.application.port.TrackEventPublisher;
import pk.nust.cuas.track.application.port.TrackStreamBroadcaster;
import pk.nust.cuas.track.application.port.TrackStreamBroadcaster.EventType;
import pk.nust.cuas.track.domain.model.Track;
import pk.nust.cuas.track.domain.model.TrackClassification;
import pk.nust.cuas.track.domain.model.TrackOutlook;

/**
 * Publishes a track change to Kafka and then to the live gRPC subscribers (ARCHITECTURE.md sections
 * 4.1.5, 5.2).
 *
 * <p>Composed rather than folded into the Kafka adapter, so neither destination knows the other
 * exists and the application layer keeps calling one port. It is the {@code @Primary} publisher,
 * which is what lets the two sweeps and the correlation service stay untouched by the existence of
 * a streaming API.
 *
 * <p><b>Kafka first, and its failure propagates; the broadcast is best-effort and its failure does
 * not.</b> The topics are the durable record — a lost track event there means a downstream service
 * never learns of a track — so a Kafka failure must roll the transaction back and reprocess. The
 * gRPC stream is a live feed with no replay: a subscriber that misses an event has missed it, and
 * failing the correlation transaction over one slow consumer would let a subscriber stop the
 * tracker. That asymmetry is the whole reason the two are ordered this way.
 */
@Component
@Primary
public class BroadcastingTrackEventPublisher implements TrackEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(BroadcastingTrackEventPublisher.class);

    private final TrackEventKafkaPublisher kafka;
    private final TrackStreamBroadcaster broadcaster;

    public BroadcastingTrackEventPublisher(TrackEventKafkaPublisher kafka,
                                           TrackStreamBroadcaster broadcaster) {
        this.kafka = kafka;
        this.broadcaster = broadcaster;
    }

    @Override
    public void publishCreated(Track track, TrackOutlook outlook) {
        kafka.publishCreated(track, outlook);
        broadcast(EventType.CREATED, track, outlook);
    }

    @Override
    public void publishUpdated(Track track, TrackOutlook outlook) {
        kafka.publishUpdated(track, outlook);
        broadcast(EventType.UPDATED, track, outlook);
    }

    @Override
    public void publishLost(Track track) {
        kafka.publishLost(track);
        broadcast(EventType.LOST, track, TrackOutlook.none());
    }

    @Override
    public void publishMerged(Track merged) {
        kafka.publishMerged(merged);
        broadcast(EventType.MERGED, merged, TrackOutlook.none());
    }

    /**
     * A classification is published to Kafka but not broadcast on the gRPC stream.
     *
     * <p>{@code TrackEventType} is the lifecycle vocabulary, and a classification is not a lifecycle
     * transition — the track's status is unchanged. Inventing a fifth event type for it would widen a
     * closed enum for something the next {@code UPDATED} already carries.
     */
    @Override
    public void publishClassified(Track track, TrackClassification classification) {
        kafka.publishClassified(track, classification);
    }

    private void broadcast(EventType type, Track track, TrackOutlook outlook) {
        try {
            broadcaster.broadcast(type, track, outlook);
        } catch (RuntimeException ex) {
            // Deliberately swallowed: see the class note. The event is already durable on Kafka.
            log.warn("Failed to broadcast {} for track {} to gRPC subscribers", type, track.id(), ex);
        }
    }
}
