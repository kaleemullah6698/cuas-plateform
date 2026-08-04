package pk.nust.cuas.ingestion.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * JPA mapping for the {@code sensors} registry row. A separate type from the domain model by
 * design (CLAUDE.md section 2): the domain never sees this class.
 */
@Entity
@Table(name = "sensors")
public class SensorEntity {

    @Id
    @Column(name = "sensor_id")
    private String sensorId;

    @Column(name = "modality", nullable = false)
    private String modality;

    @Column(name = "first_seen_at", nullable = false)
    private Instant firstSeenAt;

    @Column(name = "last_seen_at", nullable = false)
    private Instant lastSeenAt;

    protected SensorEntity() {
    }

    public SensorEntity(String sensorId, String modality, Instant seenAt) {
        this.sensorId = sensorId;
        this.modality = modality;
        this.firstSeenAt = seenAt;
        this.lastSeenAt = seenAt;
    }

    public void observed(String modality, Instant seenAt) {
        this.modality = modality;
        this.lastSeenAt = seenAt;
    }

    public String sensorId() {
        return sensorId;
    }
}
