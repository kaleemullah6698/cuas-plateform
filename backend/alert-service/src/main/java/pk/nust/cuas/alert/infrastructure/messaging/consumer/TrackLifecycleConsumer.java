package pk.nust.cuas.alert.infrastructure.messaging.consumer;

import com.google.protobuf.InvalidProtocolBufferException;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import pk.nust.cuas.alert.application.AlertLifecycleService;
import pk.nust.cuas.alert.common.AlertProtoMapper;
import pk.nust.cuas.alert.infrastructure.messaging.AlertTopics;
import pk.nust.cuas.track.v1.Track;

/**
 * Closes alerts whose track no longer exists (ARCHITECTURE.md sections 4.1.5, 4.1.8).
 *
 * <p>A lost track has left the airspace or the sensors stopped seeing it; a merged track has been
 * absorbed into an older one and its identity has been retired. In both cases every open alert about
 * it describes something that no longer exists, and leaving them open would make the alert queue
 * accumulate work nobody can do — which is how an operator learns to ignore it.
 *
 * <p><strong>Auto-resolved and INCONCLUSIVE.</strong> The system knows the track ended; it does not
 * know whether the alert had been right. Recording a verdict it cannot support would poison the
 * true-positive ratio that dispositions exist to produce, and {@code autoResolved} is what keeps a
 * closed alert from being counted as a reviewed one.
 */
@Component
public class TrackLifecycleConsumer {

    private static final Logger log = LoggerFactory.getLogger(TrackLifecycleConsumer.class);

    private final AlertLifecycleService alerts;

    public TrackLifecycleConsumer(AlertLifecycleService alerts) {
        this.alerts = alerts;
    }

    @KafkaListener(topics = AlertTopics.TRACK_LOST,
            groupId = "${spring.kafka.consumer.group-id:alert-service}")
    public void onTrackLost(ConsumerRecord<String, byte[]> record) {
        close(record, "the track was lost");
    }

    @KafkaListener(topics = AlertTopics.TRACK_MERGED,
            groupId = "${spring.kafka.consumer.group-id:alert-service}")
    public void onTrackMerged(ConsumerRecord<String, byte[]> record) {
        // The merged track is the younger one, and it is the one being retired. Its alerts close;
        // the survivor keeps its own, and will raise new ones from its own threat transitions.
        close(record, "the track was merged into another");
    }

    private void close(ConsumerRecord<String, byte[]> record, String reason) {
        try {
            Track track = Track.parseFrom(record.value());
            AlertProtoMapper.parseUuid(track.getTrackId()).ifPresentOrElse(
                    trackId -> alerts.closeAlertsFor(trackId, reason),
                    () -> log.warn("Skipping a track lifecycle event with an unusable id at offset"
                            + " {}", record.offset()));
        } catch (InvalidProtocolBufferException ex) {
            log.warn("Skipping an unparseable track lifecycle event at offset {}",
                    record.offset(), ex);
        }
    }
}
