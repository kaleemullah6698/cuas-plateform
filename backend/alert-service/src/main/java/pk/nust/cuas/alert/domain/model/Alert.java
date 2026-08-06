package pk.nust.cuas.alert.domain.model;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * An operator-facing alert — the aggregate root of this service (ARCHITECTURE.md section 4.1.8).
 *
 * <p><strong>Mutable, with every transition guarded.</strong> Unlike a threat assessment, an alert
 * is a thing that has a working life: it is raised, acknowledged, escalated, and resolved, and each
 * of those is a fact about the same object rather than a new one. The transitions are methods here
 * rather than field assignments in a service, so an illegal one — acknowledging a resolved alert,
 * escalating an acknowledged one — is impossible rather than merely unlikely.
 *
 * <p><strong>The escalation deadline lives on the aggregate, not only in Redis.</strong> The
 * scheduler (section 4.1.8) is an index that makes "which alerts are due?" cheap; it is not the
 * source of truth. If Redis is lost, {@code escalatesAt} is still on the row and the schedule can be
 * rebuilt — whereas a deadline that existed only in a cache would mean an unacknowledged critical
 * alert silently stopping its escalation the moment that cache restarted.
 */
public final class Alert {

    private final UUID alertId;
    private final String reference;
    private final DedupKey dedupKey;

    private final UUID trackId;
    private final String sectorId;
    private final UUID assessmentId;
    private final Severity severity;
    private final Recommendation recommendation;
    private final double threatScore;
    private final String title;
    private final String summary;
    private final Instant raisedAt;
    private final String correlationId;

    private AlertStatus status;
    private Instant acknowledgedAt;
    private Instant resolvedAt;
    private int escalationLevel;
    private Instant escalatesAt;
    private int occurrenceCount;
    private UUID incidentId;
    private String assignedTo;

    @SuppressWarnings("checkstyle:ParameterNumber")
    private Alert(UUID alertId, String reference, DedupKey dedupKey, UUID trackId, String sectorId,
                  UUID assessmentId, Severity severity, Recommendation recommendation,
                  double threatScore, String title, String summary, Instant raisedAt,
                  String correlationId, AlertStatus status, Instant acknowledgedAt,
                  Instant resolvedAt, int escalationLevel, Instant escalatesAt, int occurrenceCount,
                  UUID incidentId, String assignedTo) {
        this.alertId = Objects.requireNonNull(alertId, "alertId");
        this.reference = Objects.requireNonNull(reference, "reference");
        this.dedupKey = Objects.requireNonNull(dedupKey, "dedupKey");
        this.trackId = Objects.requireNonNull(trackId, "trackId");
        this.sectorId = sectorId == null ? "" : sectorId;
        this.assessmentId = assessmentId;
        this.severity = Objects.requireNonNull(severity, "severity");
        this.recommendation = Objects.requireNonNull(recommendation, "recommendation");
        this.threatScore = threatScore;
        this.title = title == null ? "" : title;
        this.summary = summary == null ? "" : summary;
        this.raisedAt = Objects.requireNonNull(raisedAt, "raisedAt");
        this.correlationId = correlationId == null ? "" : correlationId;
        this.status = Objects.requireNonNull(status, "status");
        this.acknowledgedAt = acknowledgedAt;
        this.resolvedAt = resolvedAt;
        this.escalationLevel = escalationLevel;
        this.escalatesAt = escalatesAt;
        this.occurrenceCount = occurrenceCount;
        this.incidentId = incidentId;
        this.assignedTo = assignedTo;
    }

    /**
     * Raises a new alert.
     *
     * @param alertId        UUIDv7 — time-sortable
     * @param reference      the operator-facing handle, e.g. {@code ALT-9912}
     * @param dedupKey       its deduplication identity
     * @param trackId        the track
     * @param sectorId       its sector
     * @param assessmentId   the assessment that justified raising it
     * @param severity       the level that raised it
     * @param recommendation what threat-assessment advised
     * @param threatScore    the composite score
     * @param title          one operator-facing line
     * @param summary        the rationale
     * @param raisedAt       platform clock
     * @param escalatesAt    when the first escalation fires if nobody acknowledges
     * @param correlationId  end-to-end trace
     * @return the alert
     */
    @SuppressWarnings("checkstyle:ParameterNumber")
    public static Alert raise(UUID alertId, String reference, DedupKey dedupKey, UUID trackId,
                              String sectorId, UUID assessmentId, Severity severity,
                              Recommendation recommendation, double threatScore, String title,
                              String summary, Instant raisedAt, Instant escalatesAt,
                              String correlationId) {
        return new Alert(alertId, reference, dedupKey, trackId, sectorId, assessmentId, severity,
                recommendation, threatScore, title, summary, raisedAt, correlationId,
                AlertStatus.RAISED, null, null, 0, escalatesAt, 1, null, null);
    }

    /** Rebuilds an alert from storage. Applies no rules — the row is already the result of them. */
    @SuppressWarnings("checkstyle:ParameterNumber")
    public static Alert reconstitute(UUID alertId, String reference, DedupKey dedupKey, UUID trackId,
                                     String sectorId, UUID assessmentId, Severity severity,
                                     Recommendation recommendation, double threatScore, String title,
                                     String summary, Instant raisedAt, String correlationId,
                                     AlertStatus status, Instant acknowledgedAt, Instant resolvedAt,
                                     int escalationLevel, Instant escalatesAt, int occurrenceCount,
                                     UUID incidentId, String assignedTo) {
        return new Alert(alertId, reference, dedupKey, trackId, sectorId, assessmentId, severity,
                recommendation, threatScore, title, summary, raisedAt, correlationId, status,
                acknowledgedAt, resolvedAt, escalationLevel, escalatesAt, occurrenceCount,
                incidentId, assignedTo);
    }

    // ── Transitions ─────────────────────────────────────────────────────────────────────────────

    /**
     * Records that the same condition recurred inside the suppression window.
     *
     * <p>The count goes up; nothing else changes, and no new alert is raised. That is what
     * suppression is for — but the count is what stops suppression from also hiding <em>intensity</em>,
     * so an operator can tell a condition that happened once from one still happening.
     *
     * <p>The escalation deadline is deliberately <em>not</em> pushed back. A recurrence is the
     * condition persisting, not somebody attending to it, and letting a recurring threat repeatedly
     * postpone its own escalation would let the most persistent alert be the one that never reaches
     * a supervisor.
     */
    public void recordRecurrence() {
        requireOpen("record a recurrence on");
        occurrenceCount++;
    }

    /**
     * Acknowledges the alert, stopping its escalation timer.
     *
     * @param at when it was acknowledged
     * @throws IllegalStateException when the alert is already resolved or suppressed
     */
    public void acknowledge(Instant at) {
        requireOpen("acknowledge");
        if (status == AlertStatus.ACKNOWLEDGED) {
            // Idempotent rather than an error: two operators can click at once, and failing the
            // second would report a problem where none exists.
            return;
        }
        status = AlertStatus.ACKNOWLEDGED;
        acknowledgedAt = at;
        // The deadline is cleared, not merely ignored. A stale escalatesAt on an acknowledged alert
        // would fire the moment anything rebuilt the schedule from the database.
        escalatesAt = null;
    }

    /**
     * Assigns the alert to a user. Does not acknowledge it: assignment is "this is yours",
     * acknowledgement is "I have seen it", and only the second is evidence anybody looked.
     *
     * @param userId the assignee
     */
    public void assignTo(String userId) {
        requireOpen("assign");
        this.assignedTo = userId;
    }

    /**
     * Escalates the alert one level.
     *
     * @param at        when it escalated
     * @param nextAt    when the following escalation fires, or {@code null} at the terminal level
     * @throws IllegalStateException when the alert is not in an escalatable state
     */
    public void escalate(Instant at, Instant nextAt) {
        if (!status.isEscalatable()) {
            throw new IllegalStateException(
                    "Cannot escalate an alert that is " + status + "; only a raised or already"
                            + " escalated alert has an unanswered condition to escalate");
        }
        status = AlertStatus.ESCALATED;
        escalationLevel++;
        escalatesAt = nextAt;
    }

    /**
     * Resolves the alert.
     *
     * @param at            when it was resolved
     * @throws IllegalStateException when the alert is already terminal
     */
    public void resolve(Instant at) {
        if (status.isTerminal()) {
            throw new IllegalStateException("Alert " + reference + " is already " + status);
        }
        status = AlertStatus.RESOLVED;
        resolvedAt = at;
        escalatesAt = null;
    }

    /** Marks the alert as folded into an open one rather than delivered on its own. */
    public void suppress() {
        status = AlertStatus.SUPPRESSED;
        escalatesAt = null;
    }

    /**
     * Links the alert to an incident an operator declared from it.
     *
     * @param incidentId the incident
     */
    public void attachToIncident(UUID incidentId) {
        this.incidentId = Objects.requireNonNull(incidentId, "incidentId");
    }

    private void requireOpen(String action) {
        if (status.isTerminal()) {
            throw new IllegalStateException(
                    "Cannot " + action + " alert " + reference + ": it is already " + status);
        }
    }

    // ── Queries ─────────────────────────────────────────────────────────────────────────────────

    /** @return {@code true} when this alert still needs an operator */
    public boolean isOpen() {
        return !status.isTerminal();
    }

    /** @return when the next escalation is due, or empty when none is scheduled */
    public Optional<Instant> escalatesAt() {
        return Optional.ofNullable(escalatesAt);
    }

    public UUID alertId() {
        return alertId;
    }

    public String reference() {
        return reference;
    }

    public DedupKey dedupKey() {
        return dedupKey;
    }

    public UUID trackId() {
        return trackId;
    }

    public String sectorId() {
        return sectorId;
    }

    public Optional<UUID> assessmentId() {
        return Optional.ofNullable(assessmentId);
    }

    public Severity severity() {
        return severity;
    }

    public Recommendation recommendation() {
        return recommendation;
    }

    public double threatScore() {
        return threatScore;
    }

    public String title() {
        return title;
    }

    public String summary() {
        return summary;
    }

    public Instant raisedAt() {
        return raisedAt;
    }

    public String correlationId() {
        return correlationId;
    }

    public AlertStatus status() {
        return status;
    }

    public Optional<Instant> acknowledgedAt() {
        return Optional.ofNullable(acknowledgedAt);
    }

    public Optional<Instant> resolvedAt() {
        return Optional.ofNullable(resolvedAt);
    }

    public int escalationLevel() {
        return escalationLevel;
    }

    public int occurrenceCount() {
        return occurrenceCount;
    }

    public Optional<UUID> incidentId() {
        return Optional.ofNullable(incidentId);
    }

    public Optional<String> assignedTo() {
        return Optional.ofNullable(assignedTo);
    }
}
