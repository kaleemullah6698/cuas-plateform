package pk.nust.cuas.alert.domain.model;

import java.time.Instant;
import java.util.UUID;

/**
 * The record that somebody saw an alert (ARCHITECTURE.md sections 4.1.8, 7.5).
 *
 * <p>An acknowledgement stops the escalation timer, which makes it the most consequential thing an
 * operator can do to an alert short of resolving it. It therefore names the actor: the audit chain
 * must be able to answer who silenced a critical alert, and an anonymous acknowledgement would make
 * that unanswerable.
 *
 * @param alertId    the alert
 * @param actorId    the user id
 * @param actorName  the display name, for the timeline an operator reads
 * @param note       optional free text
 * @param occurredAt platform clock
 */
public record Acknowledgement(
        UUID alertId, String actorId, String actorName, String note, Instant occurredAt) {

    public Acknowledgement {
        if (alertId == null || occurredAt == null) {
            throw new IllegalArgumentException("An acknowledgement needs an alert and a time");
        }
        if (actorId == null || actorId.isBlank()) {
            throw new IllegalArgumentException(
                    "An acknowledgement must name its actor — it stops the escalation timer, and the"
                            + " audit chain has to be able to say who did that");
        }
        actorName = actorName == null ? "" : actorName;
        note = note == null ? "" : note;
    }
}
