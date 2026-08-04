package pk.nust.cuas.ingestion.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * JPA mapping for the {@code sensor_health} row: a sensor's liveness and measured clock drift.
 */
@Entity
@Table(name = "sensor_health")
public class SensorHealthEntity {

    @Id
    @Column(name = "sensor_id")
    private String sensorId;

    @Column(name = "status", nullable = false)
    private String status;

    @Column(name = "last_detection_at")
    private Instant lastDetectionAt;

    @Column(name = "clock_drift_ms", nullable = false)
    private long clockDriftMs;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected SensorHealthEntity() {
    }

    public SensorHealthEntity(String sensorId, String status, Instant now) {
        this.sensorId = sensorId;
        this.status = status;
        this.clockDriftMs = 0L;
        this.updatedAt = now;
    }

    public void recordDetection(Instant ingestTime, long clockDriftMs, String status) {
        this.lastDetectionAt = ingestTime;
        this.clockDriftMs = clockDriftMs;
        this.status = status;
        this.updatedAt = ingestTime;
    }

    public void updateStatus(String status, Instant now) {
        this.status = status;
        this.updatedAt = now;
    }

    public String sensorId() {
        return sensorId;
    }

    public String status() {
        return status;
    }

    public Instant lastDetectionAt() {
        return lastDetectionAt;
    }

    public long clockDriftMs() {
        return clockDriftMs;
    }
}
