package pk.nust.cuas.ingestion.infrastructure.messaging.consumer;

import com.google.protobuf.InvalidProtocolBufferException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import pk.nust.cuas.commons.correlation.CorrelationId;
import pk.nust.cuas.detection.v1.DetectionRecord;
import pk.nust.cuas.ingestion.application.NormalizationService;
import pk.nust.cuas.ingestion.infrastructure.messaging.IngestionTopics;

/**
 * Consumes {@code sensor.detection.raw.v1} and drives normalisation (ARCHITECTURE.md section
 * 4.1.4).
 *
 * <p>This is the consuming half of the raw topic's dual role: the intake adapters publish here, and
 * this reads it back off the intake thread so a burst is absorbed by Kafka rather than blocking a
 * sensor (section 5.6). Concurrency is left to the container so it matches the partition count; a
 * sensor's records stay ordered because they share a partition key.
 *
 * <p>A malformed payload is logged and skipped: it can never parse on retry, so redelivering it
 * would only stall the partition. Everything else propagates, leaving the offset uncommitted so the
 * record is retried — the at-least-once behaviour the dedup window makes safe.
 */
@Component
public class RawDetectionConsumer {

    private static final Logger log = LoggerFactory.getLogger(RawDetectionConsumer.class);

    private final NormalizationService normalizationService;

    public RawDetectionConsumer(NormalizationService normalizationService) {
        this.normalizationService = normalizationService;
    }

    @KafkaListener(
            topics = IngestionTopics.RAW,
            groupId = "${spring.kafka.consumer.group-id:sensor-ingestion}")
    public void onRawDetection(byte[] payload) {
        DetectionRecord record;
        try {
            record = DetectionRecord.parseFrom(payload);
        } catch (InvalidProtocolBufferException ex) {
            log.error("Discarding malformed raw detection ({} bytes): it cannot parse on retry",
                    payload.length, ex);
            return;
        }

        // Continue the trace the intake established, so raw, normalized, and health share one id.
        CorrelationId.set(correlationId(record));
        try {
            normalizationService.normalize(record);
        } finally {
            CorrelationId.clear();
        }
    }

    private String correlationId(DetectionRecord record) {
        return record.getCorrelationId().isBlank()
                ? CorrelationId.generate()
                : record.getCorrelationId();
    }
}
