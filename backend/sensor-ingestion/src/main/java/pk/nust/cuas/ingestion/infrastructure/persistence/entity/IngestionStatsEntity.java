package pk.nust.cuas.ingestion.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * JPA mapping for the {@code ingestion_stats} counters. Every accepted, duplicate, and invalid
 * detection also counts as received, so the received total is advanced alongside each outcome.
 */
@Entity
@Table(name = "ingestion_stats")
public class IngestionStatsEntity {

    @Id
    @Column(name = "sensor_id")
    private String sensorId;

    @Column(name = "received_total", nullable = false)
    private long receivedTotal;

    @Column(name = "normalized_total", nullable = false)
    private long normalizedTotal;

    @Column(name = "duplicate_total", nullable = false)
    private long duplicateTotal;

    @Column(name = "invalid_total", nullable = false)
    private long invalidTotal;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected IngestionStatsEntity() {
    }

    public IngestionStatsEntity(String sensorId, Instant now) {
        this.sensorId = sensorId;
        this.updatedAt = now;
    }

    public void countNormalized(Instant now) {
        this.receivedTotal++;
        this.normalizedTotal++;
        this.updatedAt = now;
    }

    public void countDuplicate(Instant now) {
        this.receivedTotal++;
        this.duplicateTotal++;
        this.updatedAt = now;
    }

    public void countInvalid(Instant now) {
        this.receivedTotal++;
        this.invalidTotal++;
        this.updatedAt = now;
    }
}
