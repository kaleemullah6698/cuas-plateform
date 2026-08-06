package pk.nust.cuas.alert.infrastructure.persistence.entity;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

/**
 * JPA mapping for an {@code incidents} row and its track and alert sets.
 *
 * <p>The two sets are {@code @ElementCollection}s rather than associations: a track id here is a
 * reference into a database track-correlation owns (section 4.1.5), so there is no entity to
 * associate with. Database-per-service is absolute, and modelling the link as a foreign key to a
 * table this service does not own would be the first step to reading it.
 */
@Entity
@Table(name = "incidents")
public class IncidentEntity {

    @Id
    @Column(name = "incident_id")
    private UUID incidentId;

    @Column(name = "reference", nullable = false)
    private String reference;

    @Column(name = "status", nullable = false)
    private String status;

    @Column(name = "severity", nullable = false)
    private String severity;

    @Column(name = "title", nullable = false)
    private String title;

    @Column(name = "sector_id", nullable = false)
    private String sectorId;

    @Column(name = "declared_by", nullable = false)
    private String declaredBy;

    @Column(name = "declared_by_name", nullable = false)
    private String declaredByName;

    @Column(name = "assigned_to")
    private String assignedTo;

    @Column(name = "assigned_to_name", nullable = false)
    private String assignedToName;

    @Column(name = "opened_at", nullable = false)
    private Instant openedAt;

    @Column(name = "closed_at")
    private Instant closedAt;

    @Column(name = "closure_disposition")
    private String closureDisposition;

    @Column(name = "closure_summary", nullable = false)
    private String closureSummary;

    @Column(name = "decision_system_recommended")
    private String decisionSystemRecommended;

    @Column(name = "decision_operator", nullable = false)
    private String decisionOperator;

    @Column(name = "decision_authorised_by")
    private String decisionAuthorisedBy;

    @Column(name = "decision_authorised_by_name", nullable = false)
    private String decisionAuthorisedByName;

    @Column(name = "decision_authorised_at")
    private Instant decisionAuthorisedAt;

    @Column(name = "decision_audit_ref", nullable = false)
    private String decisionAuditRef;

    /**
     * Always {@code NONE}; the column carries a one-value CHECK. Stored rather than assumed so an
     * exported bundle asserts it (section 2.4.3) instead of leaving it to be inferred from absence.
     */
    @Column(name = "decision_engagement_action", nullable = false)
    private String decisionEngagementAction;

    @Column(name = "correlation_id", nullable = false)
    private String correlationId;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "incident_tracks",
            joinColumns = @JoinColumn(name = "incident_id"))
    @Column(name = "track_id", nullable = false)
    private Set<UUID> trackIds = new LinkedHashSet<>();

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "incident_alerts",
            joinColumns = @JoinColumn(name = "incident_id"))
    @Column(name = "alert_id", nullable = false)
    private Set<UUID> alertIds = new LinkedHashSet<>();

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    protected IncidentEntity() {
    }

    @SuppressWarnings("checkstyle:ParameterNumber")
    public IncidentEntity(UUID incidentId, String reference, String status, String severity,
                          String title, String sectorId, String declaredBy, String declaredByName,
                          Instant openedAt, String correlationId, Instant updatedAt) {
        this.incidentId = incidentId;
        this.reference = reference;
        this.status = status;
        this.severity = severity;
        this.title = title;
        this.sectorId = sectorId;
        this.declaredBy = declaredBy;
        this.declaredByName = declaredByName;
        this.openedAt = openedAt;
        this.correlationId = correlationId;
        this.updatedAt = updatedAt;
        this.assignedToName = "";
        this.closureSummary = "";
        this.decisionOperator = "";
        this.decisionAuthorisedByName = "";
        this.decisionAuditRef = "";
        this.decisionEngagementAction = "NONE";
    }

    /** Copies the mutable state of a domain incident onto this row. */
    @SuppressWarnings("checkstyle:ParameterNumber")
    public void apply(String status, String severity, String assignedTo, String assignedToName,
                      Instant closedAt, String closureDisposition, String closureSummary,
                      Set<UUID> trackIds, Set<UUID> alertIds, Instant updatedAt) {
        this.status = status;
        this.severity = severity;
        this.assignedTo = assignedTo;
        this.assignedToName = assignedToName == null ? "" : assignedToName;
        this.closedAt = closedAt;
        this.closureDisposition = closureDisposition;
        this.closureSummary = closureSummary == null ? "" : closureSummary;
        this.trackIds = new LinkedHashSet<>(trackIds);
        this.alertIds = new LinkedHashSet<>(alertIds);
        this.updatedAt = updatedAt;
    }

    /** Records the section 2.4.3 decision chain. */
    @SuppressWarnings("checkstyle:ParameterNumber")
    public void applyDecision(String systemRecommended, String operatorDecision, String authorisedBy,
                              String authorisedByName, Instant authorisedAt, String auditRef,
                              String engagementAction) {
        this.decisionSystemRecommended = systemRecommended;
        this.decisionOperator = operatorDecision == null ? "" : operatorDecision;
        this.decisionAuthorisedBy = authorisedBy;
        this.decisionAuthorisedByName = authorisedByName == null ? "" : authorisedByName;
        this.decisionAuthorisedAt = authorisedAt;
        this.decisionAuditRef = auditRef == null ? "" : auditRef;
        this.decisionEngagementAction = engagementAction;
    }

    public UUID getIncidentId() {
        return incidentId;
    }

    public String getReference() {
        return reference;
    }

    public String getStatus() {
        return status;
    }

    public String getSeverity() {
        return severity;
    }

    public String getTitle() {
        return title;
    }

    public String getSectorId() {
        return sectorId;
    }

    public String getDeclaredBy() {
        return declaredBy;
    }

    public String getDeclaredByName() {
        return declaredByName;
    }

    public String getAssignedTo() {
        return assignedTo;
    }

    public String getAssignedToName() {
        return assignedToName;
    }

    public Instant getOpenedAt() {
        return openedAt;
    }

    public Instant getClosedAt() {
        return closedAt;
    }

    public String getClosureDisposition() {
        return closureDisposition;
    }

    public String getClosureSummary() {
        return closureSummary;
    }

    public String getDecisionSystemRecommended() {
        return decisionSystemRecommended;
    }

    public String getDecisionOperator() {
        return decisionOperator;
    }

    public String getDecisionAuthorisedBy() {
        return decisionAuthorisedBy;
    }

    public String getDecisionAuthorisedByName() {
        return decisionAuthorisedByName;
    }

    public Instant getDecisionAuthorisedAt() {
        return decisionAuthorisedAt;
    }

    public String getDecisionAuditRef() {
        return decisionAuditRef;
    }

    public String getDecisionEngagementAction() {
        return decisionEngagementAction;
    }

    public String getCorrelationId() {
        return correlationId;
    }

    public Set<UUID> getTrackIds() {
        return Set.copyOf(trackIds);
    }

    public Set<UUID> getAlertIds() {
        return Set.copyOf(alertIds);
    }
}
