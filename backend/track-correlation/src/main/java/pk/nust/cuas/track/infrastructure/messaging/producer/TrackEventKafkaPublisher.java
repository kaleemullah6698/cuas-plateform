package pk.nust.cuas.track.infrastructure.messaging.producer;

import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import pk.nust.cuas.track.application.port.TrackEventPublisher;
import pk.nust.cuas.track.common.TrackProtoMapper;
import pk.nust.cuas.track.domain.model.Track;
import pk.nust.cuas.track.domain.model.TrackClassification;
import pk.nust.cuas.track.domain.model.TrackOutlook;
import pk.nust.cuas.track.infrastructure.messaging.TrackTopics;

/**
 * Kafka adapter publishing track lifecycle events (ARCHITECTURE.md sections 4.1.5, 5.2, 5.3.2).
 *
 * <p>Sends the track keyed by track id, so a track's events stay ordered on one partition. The
 * domain-to-contract mapping is {@link TrackProtoMapper}'s, shared with the gRPC stream so the two
 * surfaces cannot describe the same track differently.
 *
 * <p>Synchronous: the send blocks on the broker acknowledgement so a failure propagates and the
 * correlation transaction rolls back, leaving the detection to be reprocessed rather than a track
 * persisted with no event announced.
 */
@Component
public class TrackEventKafkaPublisher implements TrackEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(TrackEventKafkaPublisher.class);
    private static final long SEND_TIMEOUT_SECONDS = 5;

    private final KafkaTemplate<String, byte[]> kafkaTemplate;
    private final TrackProtoMapper mapper;

    public TrackEventKafkaPublisher(KafkaTemplate<String, byte[]> kafkaTemplate, TrackProtoMapper mapper) {
        this.kafkaTemplate = kafkaTemplate;
        this.mapper = mapper;
    }

    @Override
    public void publishCreated(Track track, TrackOutlook outlook) {
        send(TrackTopics.TRACK_CREATED, track, outlook);
        log.debug("Published track.created for {}", track.id());
    }

    @Override
    public void publishUpdated(Track track, TrackOutlook outlook) {
        send(TrackTopics.TRACK_UPDATED, track, outlook);
        log.debug("Published track.updated for {}", track.id());
    }

    @Override
    public void publishLost(Track track) {
        send(TrackTopics.TRACK_LOST, track, TrackOutlook.none());
        log.debug("Published track.lost for {}", track.id());
    }

    @Override
    public void publishMerged(Track merged) {
        send(TrackTopics.TRACK_MERGED, merged, TrackOutlook.none());
        log.debug("Published track.merged for {} into {}", merged.id(), merged.mergedInto().orElseThrow());
    }

    @Override
    public void publishClassified(Track track, TrackClassification classification) {
        byte[] payload = mapper.toProto(track, TrackOutlook.none(), classification).toByteArray();
        sendPayload(TrackTopics.TRACK_CLASSIFIED, track.id().toString(), payload);
        log.debug("Published track.classified for {} as {}", track.id(), classification.label());
    }

    private void send(String topic, Track track, TrackOutlook outlook) {
        sendPayload(topic, track.id().toString(), mapper.toProto(track, outlook).toByteArray());
    }

    private void sendPayload(String topic, String key, byte[] payload) {
        try {
            kafkaTemplate.send(topic, key, payload).get(SEND_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted publishing to " + topic, ex);
        } catch (Exception ex) {
            throw new IllegalStateException("Could not publish track to " + topic, ex);
        }
    }
}
