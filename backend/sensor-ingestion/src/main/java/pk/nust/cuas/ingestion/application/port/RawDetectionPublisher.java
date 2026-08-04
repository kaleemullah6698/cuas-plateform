package pk.nust.cuas.ingestion.application.port;

import pk.nust.cuas.detection.v1.DetectionRecord;

/**
 * Publishes an accepted intake record, verbatim, to {@code sensor.detection.raw.v1}
 * (ARCHITECTURE.md sections 4.1.4, 5.2).
 *
 * <p>An output port owned by the application layer and implemented by the Kafka adapter. The record
 * is keyed by {@code sensorId} to preserve per-sensor ordering across partitions (section 4.1.4).
 */
public interface RawDetectionPublisher {

    /**
     * @param sensorId the partition key
     * @param record   the record to publish, as accepted at the intake boundary
     */
    void publish(String sensorId, DetectionRecord record);
}
