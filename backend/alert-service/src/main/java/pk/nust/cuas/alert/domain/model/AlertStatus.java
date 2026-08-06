package pk.nust.cuas.alert.domain.model;

/**
 * The alert lifecycle (ARCHITECTURE.md section 4.1.8).
 *
 * <p>{@code RESOLVED} is terminal. {@code SUPPRESSED} marks an alert that was folded into an open
 * one by the deduplication window rather than delivered on its own — it is a record that the
 * condition recurred, not a second thing for the operator to work.
 */
public enum AlertStatus {

    RAISED,
    ACKNOWLEDGED,
    ESCALATED,
    RESOLVED,
    SUPPRESSED;

    /** @return {@code true} when no further transition is possible */
    public boolean isTerminal() {
        return this == RESOLVED || this == SUPPRESSED;
    }

    /** @return {@code true} when an escalation timer should still be running for this alert */
    public boolean isEscalatable() {
        return this == RAISED || this == ESCALATED;
    }
}
