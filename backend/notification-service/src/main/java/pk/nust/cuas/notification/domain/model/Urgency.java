package pk.nust.cuas.notification.domain.model;

/**
 * How urgent a notification is, ordered least to most. Derived from the source event's threat level,
 * or a fixed level for events that carry none (a sensor going offline is a system-urgency signal).
 *
 * <p>Urgency drives two decisions: whether a recipient's minimum-severity preference lets the
 * notification through at all, and whether quiet hours may suppress it. A critical notification is
 * never suppressed — safety outranks preference.
 */
public enum Urgency {
    LOW,
    MEDIUM,
    HIGH,
    CRITICAL;

    /** @return whether this urgency meets or exceeds {@code floor} */
    public boolean atLeast(Urgency floor) {
        return this.ordinal() >= floor.ordinal();
    }

    /** @return whether this urgency always delivers, quiet hours notwithstanding */
    public boolean isCritical() {
        return this == CRITICAL;
    }
}
