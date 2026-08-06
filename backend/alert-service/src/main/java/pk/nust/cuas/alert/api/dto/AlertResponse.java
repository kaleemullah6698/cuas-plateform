package pk.nust.cuas.alert.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import pk.nust.cuas.alert.domain.model.Alert;

/**
 * An alert as the operator console reads it.
 *
 * @param alertId         its id
 * @param reference       the operator-facing handle, e.g. ALT-9912
 * @param status          RAISED | ACKNOWLEDGED | ESCALATED | RESOLVED | SUPPRESSED
 * @param severity        LOW | MEDIUM | HIGH | CRITICAL
 * @param recommendation  the advisory relayed from threat-assessment — never an engagement action
 * @param threatScore     the composite that raised it
 * @param trackId         the track
 * @param sectorId        its sector
 * @param assessmentId    the assessment that justified it, or null
 * @param title           one operator-facing line
 * @param summary         the rationale
 * @param raisedAt        when it was raised
 * @param acknowledgedAt  when it was acknowledged, or null
 * @param resolvedAt      when it was resolved, or null
 * @param escalationLevel how many rungs it has climbed
 * @param escalatesAt     when the next escalation fires, or null
 * @param occurrenceCount how many times the condition recurred inside the suppression window
 * @param incidentId      the investigation it was escalated into, or null
 * @param assignedTo      the operator it is assigned to, or null
 */
@Schema(description = "An operator-facing alert")
public record AlertResponse(
        String alertId,
        String reference,
        String status,
        String severity,
        String recommendation,
        double threatScore,
        String trackId,
        String sectorId,
        String assessmentId,
        String title,
        String summary,
        Instant raisedAt,
        Instant acknowledgedAt,
        Instant resolvedAt,
        int escalationLevel,
        Instant escalatesAt,
        int occurrenceCount,
        String incidentId,
        String assignedTo) {

    /**
     * @param alert the domain alert
     * @return its wire form
     */
    public static AlertResponse from(Alert alert) {
        return new AlertResponse(
                alert.alertId().toString(),
                alert.reference(),
                alert.status().name(),
                alert.severity().name(),
                alert.recommendation().name(),
                alert.threatScore(),
                alert.trackId().toString(),
                alert.sectorId(),
                alert.assessmentId().map(java.util.UUID::toString).orElse(null),
                alert.title(),
                alert.summary(),
                alert.raisedAt(),
                alert.acknowledgedAt().orElse(null),
                alert.resolvedAt().orElse(null),
                alert.escalationLevel(),
                alert.escalatesAt().orElse(null),
                alert.occurrenceCount(),
                alert.incidentId().map(java.util.UUID::toString).orElse(null),
                alert.assignedTo().orElse(null));
    }
}
