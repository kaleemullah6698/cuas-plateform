package pk.nust.cuas.alert.domain.model;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * One step up an alert's escalation ladder (ARCHITECTURE.md section 4.1.8).
 *
 * <p>Usually produced by the scheduler rather than by a person, which is why {@code actorId} is
 * optional here and mandatory on {@link Acknowledgement}: an escalation nobody triggered is the
 * normal case, and the one worth paging about.
 *
 * @param alertId         the alert
 * @param previousLevel   the level before
 * @param currentLevel    the level now
 * @param trigger         why it fired
 * @param actorId         the operator who escalated manually, or null when the scheduler did
 * @param occurredAt      platform clock
 * @param nextEscalatesAt when the following escalation fires, or null at the terminal level
 */
public record Escalation(
        UUID alertId, int previousLevel, int currentLevel, EscalationTrigger trigger,
        String actorId, Instant occurredAt, Instant nextEscalatesAt) {

    public Escalation {
        if (alertId == null || trigger == null || occurredAt == null) {
            throw new IllegalArgumentException("An escalation needs an alert, a trigger and a time");
        }
        if (currentLevel <= previousLevel) {
            throw new IllegalArgumentException(
                    "An escalation must move up a level; " + previousLevel + " to " + currentLevel
                            + " does not");
        }
    }

    /** @return the operator who escalated manually, or empty when the scheduler did */
    public Optional<String> actor() {
        return Optional.ofNullable(actorId);
    }

    /** @return when the following escalation fires, or empty at the terminal level */
    public Optional<Instant> nextAt() {
        return Optional.ofNullable(nextEscalatesAt);
    }
}
