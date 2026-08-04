package pk.nust.cuas.ingestion.application.port;

import pk.nust.cuas.detection.v1.DetectionRecord;

/**
 * Publishes a normalised record to {@code sensor.detection.normalized.v1} (ARCHITECTURE.md sections
 * 4.1.4, 5.2), the load-bearing feed that track-correlation, audit, and reporting consume.
 *
 * <p>An output port owned by the application layer and implemented by the Kafka adapter. Keyed by
 * {@code sensorId} so a sensor's detections stay ordered on one partition.
 */
public interface NormalizedDetectionPublisher {

    /**
     * @param sensorId the partition key
     * @param record   the record with platform identity, ingest time, and drift stamped on
     */
    void publish(String sensorId, DetectionRecord record);
}
