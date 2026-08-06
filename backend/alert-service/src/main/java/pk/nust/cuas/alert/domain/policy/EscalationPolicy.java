package pk.nust.cuas.alert.domain.policy;

import java.time.Duration;
import java.time.Instant;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import pk.nust.cuas.alert.domain.model.Severity;

/**
 * The escalation ladder: how long an unacknowledged alert waits before it climbs (ARCHITECTURE.md
 * section 4.1.8).
 *
 * <p><strong>The ladder is finite.</strong> After the last rung an alert stops escalating and simply
 * stays open. An unbounded ladder would keep paging about something nobody is going to act on, and
 * the reliable consequence of that is an operator who mutes the channel — after which the next
 * genuine escalation reaches nobody.
 *
 * <p>Delays are per severity and per rung. The first rung is short at CRITICAL and long at LOW, for
 * the same reason the suppression windows differ: the cost of a late escalation and the cost of a
 * premature one are not the same at every level.
 */
public record EscalationPolicy(Map<Severity, List<Duration>> ladders) {

    /**
     * Defaults. LOW has a single long rung — a low-severity alert nobody has looked at in an hour is
     * worth mentioning once, and not worth mentioning twice.
     */
    public static final EscalationPolicy DEFAULT = new EscalationPolicy(Map.of(
            Severity.LOW, List.of(Duration.ofHours(1)),
            Severity.MEDIUM, List.of(Duration.ofMinutes(20), Duration.ofMinutes(40)),
            Severity.HIGH, List.of(Duration.ofMinutes(5), Duration.ofMinutes(10),
                    Duration.ofMinutes(20)),
            Severity.CRITICAL, List.of(Duration.ofMinutes(2), Duration.ofMinutes(5),
                    Duration.ofMinutes(10))));

    public EscalationPolicy {
        if (ladders == null) {
            throw new IllegalArgumentException("An escalation policy needs a ladder per severity");
        }
        Map<Severity, List<Duration>> copy = new EnumMap<>(Severity.class);
        ladders.forEach((severity, rungs) -> copy.put(severity, List.copyOf(rungs)));
        for (Severity severity : Severity.values()) {
            List<Duration> rungs = copy.get(severity);
            if (rungs == null || rungs.isEmpty()) {
                throw new IllegalArgumentException("No escalation ladder configured for " + severity
                        + ". A severity with no ladder would never escalate, which is a decision"
                        + " that must be made explicitly rather than by omission.");
            }
            rungs.forEach(rung -> {
                if (rung.isNegative() || rung.isZero()) {
                    throw new IllegalArgumentException(
                            "Escalation delays must be positive; " + severity + " has " + rung);
                }
            });
        }
        ladders = Map.copyOf(copy);
    }

    /**
     * When the escalation after {@code currentLevel} is due.
     *
     * @param severity     the alert's severity
     * @param currentLevel how many times it has already escalated; 0 for a freshly raised alert
     * @param from         the instant to measure from
     * @return the deadline, or empty when the ladder is exhausted
     */
    public Optional<Instant> nextEscalationAt(Severity severity, int currentLevel, Instant from) {
        List<Duration> rungs = ladders.get(severity);
        if (currentLevel < 0 || currentLevel >= rungs.size()) {
            return Optional.empty();
        }
        return Optional.of(from.plus(rungs.get(currentLevel)));
    }

    /**
     * @param severity the alert's severity
     * @return how many times an alert at that severity can escalate
     */
    public int maxLevel(Severity severity) {
        return ladders.get(severity).size();
    }
}
