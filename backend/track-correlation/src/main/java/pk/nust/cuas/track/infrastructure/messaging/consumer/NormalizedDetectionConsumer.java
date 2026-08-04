package pk.nust.cuas.track.infrastructure.messaging.consumer;

import com.google.protobuf.InvalidProtocolBufferException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import pk.nust.cuas.commons.correlation.CorrelationId;
import pk.nust.cuas.detection.v1.DetectionRecord;
import pk.nust.cuas.track.application.DetectionCorrelationService;
import pk.nust.cuas.track.infrastructure.messaging.TrackTopics;

/**
 * Consumes {@code sensor.detection.normalized.v1} and correlates each detection into a track
 * (ARCHITECTURE.md section 4.1.5).
 *
 * <p>Concurrency is left at one: the foundation associates single-threaded, which is what lets the
 * read-modify-write on a track be a plain transaction. Concurrent per-sector processing is M5.2.
 *
 * <p>A malformed payload is logged and skipped — it cannot parse on retry, so redelivering it would
 * stall the partition. Anything else propagates, leaving the offset uncommitted so the record is
 * reprocessed; the correlation service's idempotency guard makes that safe.
 */
@Component
public class NormalizedDetectionConsumer {

    private static final Logger log = LoggerFactory.getLogger(NormalizedDetectionConsumer.class);

    private final DetectionCorrelationService correlation;

    public NormalizedDetectionConsumer(DetectionCorrelationService correlation) {
        this.correlation = correlation;
    }

    @KafkaListener(
            topics = TrackTopics.NORMALIZED_DETECTIONS,
            groupId = "${spring.kafka.consumer.group-id:track-correlation}",
            concurrency = "1")
    public void onNormalizedDetection(byte[] payload) {
        DetectionRecord record;
        try {
            record = DetectionRecord.parseFrom(payload);
        } catch (InvalidProtocolBufferException ex) {
            log.error("Discarding malformed normalized detection ({} bytes): it cannot parse on retry",
                    payload.length, ex);
            return;
        }

        CorrelationId.set(correlationId(record));
        try {
            correlation.correlate(record);
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
