package pk.nust.cuas.ingestion.infrastructure.messaging.producer;

import org.springframework.stereotype.Component;
import pk.nust.cuas.detection.v1.DetectionRecord;
import pk.nust.cuas.ingestion.application.port.RawDetectionPublisher;
import pk.nust.cuas.ingestion.infrastructure.messaging.IngestionTopics;

/**
 * Kafka adapter publishing accepted intake records to {@code sensor.detection.raw.v1}.
 *
 * <p>Synchronous: the intake boundary only tells a sensor its detection was accepted once the raw
 * write is durable, so an ack is never a lie. Keyed by {@code sensorId} to keep a sensor's stream
 * ordered on one partition (ARCHITECTURE.md section 4.1.4).
 */
@Component
public class RawDetectionKafkaPublisher implements RawDetectionPublisher {

    private final KafkaByteSender sender;

    public RawDetectionKafkaPublisher(KafkaByteSender sender) {
        this.sender = sender;
    }

    @Override
    public void publish(String sensorId, DetectionRecord record) {
        sender.sendSync(IngestionTopics.RAW, sensorId, record.toByteArray());
    }
}
