package pk.nust.cuas.ingestion.domain.model;

import java.time.Instant;
import java.util.Optional;

/**
 * A point-in-time view of one sensor's health, assembled from its persisted state for publication
 * on {@code sensor.health.v1}.
 *
 * @param sensorId        the sensor
 * @param status          the liveness judged at assembly time
 * @param lastDetectionAt the platform clock at the last accepted detection, or empty if none
 * @param clockDriftMs    the current EWMA clock-drift estimate
 */
public record SensorHealthSnapshot(
        String sensorId,
        HealthStatus status,
        Optional<Instant> lastDetectionAt,
        long clockDriftMs) {

    public SensorHealthSnapshot {
        if (lastDetectionAt == null) {
            throw new IllegalArgumentException("lastDetectionAt must not be null; use Optional.empty()");
        }
    }
}
