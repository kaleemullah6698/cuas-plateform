package pk.nust.cuas.reporting.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** JPA mapping for {@code report_events} — one projected domain fact in the read model. */
@Entity
@Table(name = "report_events")
public class ReportEventEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "event_type", nullable = false)
    private String eventType;

    @Column(name = "subject_id")
    private UUID subjectId;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    @Column(name = "event_day", nullable = false)
    private LocalDate eventDay;

    @Column(name = "attributes", nullable = false)
    private String attributes;

    @Column(name = "dedup_key", nullable = false)
    private String dedupKey;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected ReportEventEntity() {
    }

    public ReportEventEntity(String eventType, UUID subjectId, Instant occurredAt, LocalDate eventDay,
                             String attributes, String dedupKey, Instant createdAt) {
        this.eventType = eventType;
        this.subjectId = subjectId;
        this.occurredAt = occurredAt;
        this.eventDay = eventDay;
        this.attributes = attributes;
        this.dedupKey = dedupKey;
        this.createdAt = createdAt;
    }

    public String eventType() {
        return eventType;
    }

    public UUID subjectId() {
        return subjectId;
    }

    public Instant occurredAt() {
        return occurredAt;
    }

    public String attributes() {
        return attributes;
    }

    public String dedupKey() {
        return dedupKey;
    }
}
