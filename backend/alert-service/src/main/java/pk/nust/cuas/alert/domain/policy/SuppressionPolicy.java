package pk.nust.cuas.alert.domain.policy;

import java.time.Duration;
import java.time.Instant;
import java.util.EnumMap;
import java.util.Map;
import pk.nust.cuas.alert.domain.model.Alert;
import pk.nust.cuas.alert.domain.model.Severity;

/**
 * How long the same condition is folded into an open alert rather than raising a new one
 * (ARCHITECTURE.md sections 4.1.8, 5.7).
 *
 * <p><strong>The window is shorter at higher severity, and that is the whole design.</strong> The
 * naive choice is one window for everything, and it is wrong in both directions at once: long enough
 * to keep a flapping LOW track from filling the queue, it would also silence a CRITICAL track that
 * kept re-triggering; short enough to keep CRITICAL responsive, it would let a noisy LOW track raise
 * an alert a minute. Suppression exists to protect the operator's attention, and attention is worth
 * spending differently at different severities.
 *
 * <p>Framework-free and pure: given an alert and a time it returns the same answer every time, so
 * two instances processing the same transition concurrently cannot disagree about whether it is a
 * duplicate. The database's unique constraint is what settles the race they can still lose.
 */
public record SuppressionPolicy(Map<Severity, Duration> windows) {

    /**
     * Defaults. CRITICAL is deliberately short rather than zero: even a critical condition
     * re-triggering four times a second is one thing happening, and four alerts for it would be the
     * duplicate-alert failure section 5.7 names, arriving at the worst possible moment.
     */
    public static final SuppressionPolicy DEFAULT = new SuppressionPolicy(Map.of(
            Severity.LOW, Duration.ofMinutes(15),
            Severity.MEDIUM, Duration.ofMinutes(10),
            Severity.HIGH, Duration.ofMinutes(5),
            Severity.CRITICAL, Duration.ofMinutes(2)));

    public SuppressionPolicy {
        if (windows == null) {
            throw new IllegalArgumentException("A suppression policy needs a window per severity");
        }
        Map<Severity, Duration> copy = new EnumMap<>(Severity.class);
        copy.putAll(windows);
        for (Severity severity : Severity.values()) {
            Duration window = copy.get(severity);
            if (window == null) {
                throw new IllegalArgumentException("No suppression window configured for " + severity
                        + ". A severity with no window would suppress nothing or everything,"
                        + " depending on which default a caller happened to assume.");
            }
            if (window.isNegative()) {
                throw new IllegalArgumentException(
                        "Suppression window for " + severity + " cannot be negative");
            }
        }
        windows = Map.copyOf(copy);
    }

    /**
     * @param severity the severity
     * @return how long a recurrence is folded into an open alert at that severity
     */
    public Duration windowFor(Severity severity) {
        return windows.get(severity);
    }

    /**
     * Decides whether a recurrence of the same condition should be folded into an existing alert.
     *
     * <p>Measured from when the alert was <em>raised</em>, not from the last recurrence. A window
     * that reset on every recurrence would never expire for a persistently flapping track, so an
     * alert raised once could suppress its own successors indefinitely and the operator would never
     * learn the condition was still live.
     *
     * @param existing the open alert with the same dedup key
     * @param now      platform clock
     * @return {@code true} when the recurrence should be folded in rather than raised
     */
    public boolean suppresses(Alert existing, Instant now) {
        if (!existing.isOpen()) {
            return false;
        }
        Duration window = windowFor(existing.severity());
        return !now.isAfter(existing.raisedAt().plus(window));
    }
}
