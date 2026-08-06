package pk.nust.cuas.alert.domain.model;

/**
 * Why an escalation happened (ARCHITECTURE.md section 4.1.8).
 *
 * <p>Reported explicitly rather than inferred, because notification-service words the three
 * differently: "nobody has looked at this" is a different message from "this got worse", and an
 * operator who receives the wrong one learns to distrust both.
 */
public enum EscalationTrigger {

    /** The acknowledgement window elapsed with nobody acknowledging. */
    UNACKNOWLEDGED,

    /** The threat level rose while the alert was open. */
    LEVEL_INCREASE,

    /** An operator escalated deliberately. */
    MANUAL
}
