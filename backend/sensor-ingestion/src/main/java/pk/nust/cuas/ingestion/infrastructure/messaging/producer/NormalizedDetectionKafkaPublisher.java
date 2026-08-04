package pk.nust.cuas.ingestion.infrastructure.messaging.producer;

import org.springframework.stereotype.Component;
import pk.nust.cuas.detection.v1.DetectionRecord;
import pk.nust.cuas.ingestion.application.port.NormalizedDetectionPublisher;
import pk.nust.cuas.ingestion.infrastructure.messaging.IngestionTopics;

/**
 * Kafka adapter publishing normalised records to {@code sensor.detection.normalized.v1}.
 *
 * <p>Synchronous: this runs on the raw-consumer thread, and blocking until the broker acknowledges
 * means the consumer only commits its offset behind a durable normalized write. A publish failure
 * therefore leaves the offset uncommitted and the record is retried — at-least-once, which the
 * dedup window downstream of this makes safe.
 */
@Component
public class NormalizedDetectionKafkaPublisher implements NormalizedDetectionPublisher {

    private final KafkaByteSender sender;

    public NormalizedDetectionKafkaPublisher(KafkaByteSender sender) {
        this.sender = sender;
    }

    @Override
    public void publish(String sensorId, DetectionRecord record) {
        sender.sendSync(IngestionTopics.NORMALIZED, sensorId, record.toByteArray());
    }
}
