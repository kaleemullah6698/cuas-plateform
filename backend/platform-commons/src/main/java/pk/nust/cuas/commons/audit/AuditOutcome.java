package pk.nust.cuas.commons.audit;

/**
 * How an audited action ended (ARCHITECTURE.md section 7.5, {@code outcome} column).
 *
 * <p>{@link #FAILURE} and {@link #DENIED} are distinct on purpose: a failure is an action that
 * was permitted but did not complete; a denial is an action the caller had no authority to
 * attempt. Section 7.6 forwards both to the SIEM, but only denials indicate an authorisation
 * boundary being probed.
 */
public enum AuditOutcome {

    /** The action completed. */
    SUCCESS,

    /** The action was permitted but did not complete. */
    FAILURE,

    /** The action was refused because the actor lacked authority. */
    DENIED
}
