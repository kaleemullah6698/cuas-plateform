package pk.nust.cuas.alert.domain.model;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * One line of section 2.4.3's event sequence.
 *
 * <p>The timeline is <strong>assembled from the platform's own events</strong>, not written by hand,
 * so each entry names the topic and record it came from. That is what makes the timeline evidence
 * rather than a summary: every line can be traced back to something that actually happened on a
 * topic, and a line nobody can trace is a line nobody should rely on in a review.
 *
 * <p>An empty {@code actorId} is what section 2.4.3 renders as {@code SYS} rather than {@code USER}.
 * The distinction is load-bearing — "the system created a track" and "an operator acknowledged" are
 * different kinds of fact, and a timeline that blurred them would let an automated step read as a
 * human decision.
 *
 * @param eventId     UUIDv7
 * @param incidentId  the investigation this belongs to
 * @param sourceTopic the topic the entry was derived from, e.g. {@code alert.raised.v1}
 * @param sourceId    the id of the originating record
 * @param summary     the rendered line, e.g. "threat MED to CRITICAL 0.853"
 * @param actorId     the operator, or null for a system entry
 * @param actorName   their display name
 * @param occurredAt  when the underlying thing happened
 */
public record IncidentEvent(
        UUID eventId,
        UUID incidentId,
        String sourceTopic,
        String sourceId,
        String summary,
        String actorId,
        String actorName,
        Instant occurredAt) {

    public IncidentEvent {
        if (eventId == null || incidentId == null || occurredAt == null) {
            throw new IllegalArgumentException(
                    "A timeline entry needs an id, an incident and a time");
        }
        if (summary == null || summary.isBlank()) {
            throw new IllegalArgumentException(
                    "A timeline entry must say what happened; an unrendered line is one an operator"
                            + " cannot read and a reviewer cannot check");
        }
        sourceTopic = sourceTopic == null ? "" : sourceTopic;
        sourceId = sourceId == null ? "" : sourceId;
        actorName = actorName == null ? "" : actorName;
    }

    /**
     * A line produced by the platform rather than by a person.
     *
     * @param eventId     UUIDv7
     * @param incidentId  the investigation
     * @param sourceTopic the originating topic
     * @param sourceId    the originating record
     * @param summary     the rendered line
     * @param occurredAt  when
     * @return the entry
     */
    public static IncidentEvent system(UUID eventId, UUID incidentId, String sourceTopic,
                                       String sourceId, String summary, Instant occurredAt) {
        return new IncidentEvent(eventId, incidentId, sourceTopic, sourceId, summary,
                null, "", occurredAt);
    }

    /**
     * A line produced by an operator action.
     *
     * @param eventId    UUIDv7
     * @param incidentId the investigation
     * @param sourceTopic the originating topic
     * @param sourceId   the originating record
     * @param summary    the rendered line
     * @param actorId    the operator
     * @param actorName  their display name
     * @param occurredAt when
     * @return the entry
     */
    @SuppressWarnings("checkstyle:ParameterNumber")
    public static IncidentEvent byOperator(UUID eventId, UUID incidentId, String sourceTopic,
                                           String sourceId, String summary, String actorId,
                                           String actorName, Instant occurredAt) {
        if (actorId == null || actorId.isBlank()) {
            throw new IllegalArgumentException(
                    "An operator timeline entry must name its actor; use system() otherwise");
        }
        return new IncidentEvent(eventId, incidentId, sourceTopic, sourceId, summary,
                actorId, actorName, occurredAt);
    }

    /** @return the operator, or empty for a system entry */
    public Optional<String> actor() {
        return Optional.ofNullable(actorId);
    }

    /** @return {@code true} when the platform produced this line rather than a person */
    public boolean isSystemGenerated() {
        return actorId == null;
    }
}
