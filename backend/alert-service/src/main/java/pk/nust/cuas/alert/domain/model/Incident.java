package pk.nust.cuas.alert.domain.model;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * An operator-declared investigation (ARCHITECTURE.md sections 4.1.8, 2.4.3).
 *
 * <p><strong>An incident is always declared by a person.</strong> {@code declaredBy} is a user id
 * and there is no factory that takes a service identity. Section 2.4.3's decision chain records
 * "OPERATOR DECIDED — escalate to incident" as a human act, and a system that could open one by
 * itself would make that record untrue for every incident it opened.
 *
 * <p>An incident can span several tracks and several alerts, and the sets only grow. A drone that
 * splits into two tracks, or a second drone joining the first, is one investigation — and removing a
 * track from an incident after the fact would erase part of what the investigation actually looked
 * at.
 */
public final class Incident {

    private final UUID incidentId;
    private final String reference;
    private final String declaredBy;
    private final String declaredByName;
    private final Instant openedAt;
    private final String correlationId;

    private IncidentStatus status;
    private Severity severity;
    private String title;
    private String sectorId;
    private final Set<UUID> trackIds = new LinkedHashSet<>();
    private final Set<UUID> alertIds = new LinkedHashSet<>();
    private String assignedTo;
    private String assignedToName;
    private Instant closedAt;
    private AlertDisposition closureDisposition;
    private String closureSummary;
    private DecisionChain decisionChain;

    @SuppressWarnings("checkstyle:ParameterNumber")
    private Incident(UUID incidentId, String reference, IncidentStatus status, Severity severity,
                     String title, String sectorId, String declaredBy, String declaredByName,
                     Instant openedAt, String correlationId) {
        this.incidentId = Objects.requireNonNull(incidentId, "incidentId");
        this.reference = Objects.requireNonNull(reference, "reference");
        this.status = Objects.requireNonNull(status, "status");
        this.severity = Objects.requireNonNull(severity, "severity");
        this.title = title == null ? "" : title;
        this.sectorId = sectorId == null ? "" : sectorId;
        if (declaredBy == null || declaredBy.isBlank()) {
            throw new IllegalArgumentException(
                    "An incident is declared by a person, never by a service (section 2.4.3)");
        }
        this.declaredBy = declaredBy;
        this.declaredByName = declaredByName == null ? "" : declaredByName;
        this.openedAt = Objects.requireNonNull(openedAt, "openedAt");
        this.correlationId = correlationId == null ? "" : correlationId;
    }

    /**
     * Declares a new incident.
     *
     * @param incidentId     UUIDv7
     * @param reference      the operator-facing handle, e.g. {@code INC-2026-0087}
     * @param severity       the severity it is opened at
     * @param title          what the operator sees
     * @param sectorId       the sector
     * @param declaredBy     the declaring user id — never a service
     * @param declaredByName their display name
     * @param openedAt       platform clock
     * @param correlationId  end-to-end trace
     * @return the incident
     */
    @SuppressWarnings("checkstyle:ParameterNumber")
    public static Incident declare(UUID incidentId, String reference, Severity severity,
                                   String title, String sectorId, String declaredBy,
                                   String declaredByName, Instant openedAt, String correlationId) {
        return new Incident(incidentId, reference, IncidentStatus.OPEN, severity, title, sectorId,
                declaredBy, declaredByName, openedAt, correlationId);
    }

    /** Rebuilds an incident from storage. Applies no rules — the row is already the result of them. */
    @SuppressWarnings("checkstyle:ParameterNumber")
    public static Incident reconstitute(UUID incidentId, String reference, IncidentStatus status,
                                        Severity severity, String title, String sectorId,
                                        String declaredBy, String declaredByName, Instant openedAt,
                                        String correlationId, Set<UUID> trackIds,
                                        Set<UUID> alertIds, String assignedTo,
                                        String assignedToName, Instant closedAt,
                                        AlertDisposition closureDisposition, String closureSummary,
                                        DecisionChain decisionChain) {
        Incident incident = new Incident(incidentId, reference, status, severity, title, sectorId,
                declaredBy, declaredByName, openedAt, correlationId);
        incident.trackIds.addAll(trackIds);
        incident.alertIds.addAll(alertIds);
        incident.assignedTo = assignedTo;
        incident.assignedToName = assignedToName;
        incident.closedAt = closedAt;
        incident.closureDisposition = closureDisposition;
        incident.closureSummary = closureSummary;
        incident.decisionChain = decisionChain;
        return incident;
    }

    // ── Transitions ─────────────────────────────────────────────────────────────────────────────

    /**
     * Links an alert and its track into this investigation.
     *
     * @param alert the alert
     */
    public void include(Alert alert) {
        requireOpen("add an alert to");
        alertIds.add(alert.alertId());
        trackIds.add(alert.trackId());
        // An investigation takes the severity of the worst thing in it. Keeping the severity it was
        // opened at would understate an incident that grew.
        if (alert.severity().isAbove(severity)) {
            severity = alert.severity();
        }
    }

    /**
     * Assigns the investigation.
     *
     * @param userId   the assignee
     * @param userName their display name
     */
    public void assignTo(String userId, String userName) {
        requireOpen("assign");
        this.assignedTo = userId;
        this.assignedToName = userName == null ? "" : userName;
        if (status == IncidentStatus.OPEN) {
            status = IncidentStatus.UNDER_REVIEW;
        }
    }

    /**
     * Records the decision chain — what was advised, what was decided, and by whom.
     *
     * @param chain the chain
     */
    public void recordDecision(DecisionChain chain) {
        requireOpen("record a decision on");
        this.decisionChain = Objects.requireNonNull(chain, "chain");
    }

    /**
     * Closes the investigation.
     *
     * @param disposition the verdict
     * @param summary     the closing narrative
     * @param at          platform clock
     * @throws IllegalStateException when it is already closed
     */
    public void close(AlertDisposition disposition, String summary, Instant at) {
        requireOpen("close");
        this.status = IncidentStatus.CLOSED;
        this.closureDisposition = Objects.requireNonNull(disposition, "disposition");
        this.closureSummary = summary == null ? "" : summary;
        this.closedAt = Objects.requireNonNull(at, "at");
    }

    private void requireOpen(String action) {
        if (status.isClosed()) {
            throw new IllegalStateException(
                    "Cannot " + action + " incident " + reference + ": it is closed");
        }
    }

    // ── Queries ─────────────────────────────────────────────────────────────────────────────────

    public UUID incidentId() {
        return incidentId;
    }

    public String reference() {
        return reference;
    }

    public IncidentStatus status() {
        return status;
    }

    public Severity severity() {
        return severity;
    }

    public String title() {
        return title;
    }

    public String sectorId() {
        return sectorId;
    }

    public Set<UUID> trackIds() {
        return Set.copyOf(trackIds);
    }

    public Set<UUID> alertIds() {
        return Set.copyOf(alertIds);
    }

    public String declaredBy() {
        return declaredBy;
    }

    public String declaredByName() {
        return declaredByName;
    }

    public Optional<String> assignedTo() {
        return Optional.ofNullable(assignedTo);
    }

    public String assignedToName() {
        return assignedToName == null ? "" : assignedToName;
    }

    public Instant openedAt() {
        return openedAt;
    }

    public Optional<Instant> closedAt() {
        return Optional.ofNullable(closedAt);
    }

    public Optional<AlertDisposition> closureDisposition() {
        return Optional.ofNullable(closureDisposition);
    }

    public String closureSummary() {
        return closureSummary == null ? "" : closureSummary;
    }

    public Optional<DecisionChain> decisionChain() {
        return Optional.ofNullable(decisionChain);
    }

    public String correlationId() {
        return correlationId;
    }
}
