package pk.nust.cuas.alert.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;

/**
 * JPA mapping for an {@code alerts} row.
 *
 * <p>Carries an {@code @Version} column. Two operators working the same queue, and the escalation
 * sweep running beside them, will contend for the same row; optimistic locking is what turns that
 * into a retryable conflict rather than a lost acknowledgement.
 */
@Entity
@Table(name = "alerts")
public class AlertEntity {

    @Id
    @Column(name = "alert_id")
    private UUID alertId;

    @Column(name = "reference", nullable = false)
    private String reference;

    @Column(name = "dedup_key", nullable = false)
    private String dedupKey;

    @Column(name = "status", nullable = false)
    private String status;

    @Column(name = "track_id", nullable = false)
    private UUID trackId;

    @Column(name = "sector_id", nullable = false)
    private String sectorId;

    @Column(name = "assessment_id")
    private UUID assessmentId;

    @Column(name = "severity", nullable = false)
    private String severity;

    @Column(name = "recommendation", nullable = false)
    private String recommendation;

    @Column(name = "threat_score", nullable = false)
    private double threatScore;

    @Column(name = "title", nullable = false)
    private String title;

    @Column(name = "summary", nullable = false)
    private String summary;

    @Column(name = "raised_at", nullable = false)
    private Instant raisedAt;

    @Column(name = "acknowledged_at")
    private Instant acknowledgedAt;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    @Column(name = "escalation_level", nullable = false)
    private int escalationLevel;

    @Column(name = "escalates_at")
    private Instant escalatesAt;

    @Column(name = "occurrence_count", nullable = false)
    private int occurrenceCount;

    @Column(name = "incident_id")
    private UUID incidentId;

    @Column(name = "assigned_to")
    private String assignedTo;

    @Column(name = "correlation_id", nullable = false)
    private String correlationId;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    protected AlertEntity() {
    }

    @SuppressWarnings("checkstyle:ParameterNumber")
    public AlertEntity(UUID alertId, String reference, String dedupKey, String status, UUID trackId,
                       String sectorId, UUID assessmentId, String severity, String recommendation,
                       double threatScore, String title, String summary, Instant raisedAt,
                       Instant acknowledgedAt, Instant resolvedAt, int escalationLevel,
                       Instant escalatesAt, int occurrenceCount, UUID incidentId, String assignedTo,
                       String correlationId, Instant updatedAt) {
        this.alertId = alertId;
        this.reference = reference;
        this.dedupKey = dedupKey;
        this.status = status;
        this.trackId = trackId;
        this.sectorId = sectorId;
        this.assessmentId = assessmentId;
        this.severity = severity;
        this.recommendation = recommendation;
        this.threatScore = threatScore;
        this.title = title;
        this.summary = summary;
        this.raisedAt = raisedAt;
        this.acknowledgedAt = acknowledgedAt;
        this.resolvedAt = resolvedAt;
        this.escalationLevel = escalationLevel;
        this.escalatesAt = escalatesAt;
        this.occurrenceCount = occurrenceCount;
        this.incidentId = incidentId;
        this.assignedTo = assignedTo;
        this.correlationId = correlationId;
        this.updatedAt = updatedAt;
    }

    /** Copies the mutable state of a domain alert onto this row. */
    @SuppressWarnings("checkstyle:ParameterNumber")
    public void apply(String status, Instant acknowledgedAt, Instant resolvedAt,
                      int escalationLevel, Instant escalatesAt, int occurrenceCount,
                      UUID incidentId, String assignedTo, Instant updatedAt) {
        this.status = status;
        this.acknowledgedAt = acknowledgedAt;
        this.resolvedAt = resolvedAt;
        this.escalationLevel = escalationLevel;
        this.escalatesAt = escalatesAt;
        this.occurrenceCount = occurrenceCount;
        this.incidentId = incidentId;
        this.assignedTo = assignedTo;
        this.updatedAt = updatedAt;
    }

    public UUID getAlertId() {
        return alertId;
    }

    public String getReference() {
        return reference;
    }

    public String getDedupKey() {
        return dedupKey;
    }

    public String getStatus() {
        return status;
    }

    public UUID getTrackId() {
        return trackId;
    }

    public String getSectorId() {
        return sectorId;
    }

    public UUID getAssessmentId() {
        return assessmentId;
    }

    public String getSeverity() {
        return severity;
    }

    public String getRecommendation() {
        return recommendation;
    }

    public double getThreatScore() {
        return threatScore;
    }

    public String getTitle() {
        return title;
    }

    public String getSummary() {
        return summary;
    }

    public Instant getRaisedAt() {
        return raisedAt;
    }

    public Instant getAcknowledgedAt() {
        return acknowledgedAt;
    }

    public Instant getResolvedAt() {
        return resolvedAt;
    }

    public int getEscalationLevel() {
        return escalationLevel;
    }

    public Instant getEscalatesAt() {
        return escalatesAt;
    }

    public int getOccurrenceCount() {
        return occurrenceCount;
    }

    public UUID getIncidentId() {
        return incidentId;
    }

    public String getAssignedTo() {
        return assignedTo;
    }

    public String getCorrelationId() {
        return correlationId;
    }
}
