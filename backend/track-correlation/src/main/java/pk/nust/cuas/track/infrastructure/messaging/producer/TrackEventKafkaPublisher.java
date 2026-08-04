package pk.nust.cuas.track.infrastructure.messaging.producer;

import com.google.protobuf.Timestamp;
import java.time.Instant;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import pk.nust.cuas.commons.correlation.CorrelationId;
import pk.nust.cuas.detection.v1.GeoPosition;
import pk.nust.cuas.track.application.port.TrackEventPublisher;
import pk.nust.cuas.track.domain.model.Track;
import pk.nust.cuas.track.domain.model.TrackStatus;
import pk.nust.cuas.track.infrastructure.messaging.TrackTopics;

/**
 * Kafka adapter publishing track lifecycle events (ARCHITECTURE.md sections 4.1.5, 5.2, 5.3.2).
 *
 * <p>Maps the domain {@link Track} to the {@code track.v1.Track} contract and sends it keyed by
 * track id. Synchronous: the send blocks on the broker acknowledgement so a failure propagates and
 * the correlation transaction rolls back, leaving the detection to be reprocessed rather than a
 * track persisted with no event announced.
 */
@Component
public class TrackEventKafkaPublisher implements TrackEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(TrackEventKafkaPublisher.class);
    private static final String SCHEMA_VERSION = "1.0.0";
    private static final long SEND_TIMEOUT_SECONDS = 5;

    private final KafkaTemplate<String, byte[]> kafkaTemplate;

    public TrackEventKafkaPublisher(KafkaTemplate<String, byte[]> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    @Override
    public void publishCreated(Track track) {
        send(TrackTopics.TRACK_CREATED, track);
        log.debug("Published track.created for {}", track.id());
    }

    @Override
    public void publishUpdated(Track track) {
        send(TrackTopics.TRACK_UPDATED, track);
        log.debug("Published track.updated for {}", track.id());
    }

    private void send(String topic, Track track) {
        byte[] payload = toProto(track).toByteArray();
        try {
            kafkaTemplate.send(topic, track.id().toString(), payload).get(SEND_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted publishing to " + topic, ex);
        } catch (Exception ex) {
            throw new IllegalStateException("Could not publish track to " + topic, ex);
        }
    }

    private pk.nust.cuas.track.v1.Track toProto(Track track) {
        var state = track.state();
        return pk.nust.cuas.track.v1.Track.newBuilder()
                .setTrackId(track.id().toString())
                .setSectorId(track.sector().value())
                .setStatus(toProtoStatus(track.status()))
                .setPosition(GeoPosition.newBuilder()
                        .setLatitude(state.position().latitude())
                        .setLongitude(state.position().longitude())
                        .setAltitudeM(state.position().altitudeM()))
                .setSpeedMs(state.velocity().speedMs())
                .setHeadingDeg(state.velocity().headingDeg())
                .setObservedAt(toTimestamp(state.observedAt()))
                .setFirstSeen(toTimestamp(track.firstSeen()))
                .setLastSeen(toTimestamp(track.lastSeen()))
                .setDetectionCount(track.detectionCount())
                .setLastDetectionId(state.detectionId())
                .setSchemaVersion(SCHEMA_VERSION)
                .setCorrelationId(CorrelationId.current().orElse(""))
                .build();
    }

    private pk.nust.cuas.track.v1.TrackStatus toProtoStatus(TrackStatus status) {
        return switch (status) {
            case TENTATIVE -> pk.nust.cuas.track.v1.TrackStatus.TRACK_STATUS_TENTATIVE;
            case CONFIRMED -> pk.nust.cuas.track.v1.TrackStatus.TRACK_STATUS_CONFIRMED;
        };
    }

    private Timestamp toTimestamp(Instant instant) {
        return Timestamp.newBuilder()
                .setSeconds(instant.getEpochSecond()).setNanos(instant.getNano()).build();
    }
}
