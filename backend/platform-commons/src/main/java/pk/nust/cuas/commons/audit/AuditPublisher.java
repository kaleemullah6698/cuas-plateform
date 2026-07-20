package pk.nust.cuas.commons.audit;

/**
 * Publishes audit events to the tamper-evident log.
 *
 * <p><strong>Fail-closed contract (ARCHITECTURE.md section 7.5).</strong> {@link #publish} is
 * synchronous and throws {@link AuditWriteException} when the event cannot be handed to the log.
 * Callers must let that exception propagate so the surrounding transaction rolls back: <em>an
 * operator action that cannot be recorded is an action that does not happen.</em> Swallowing the
 * exception, or publishing asynchronously and ignoring the result, defeats the control.
 */
public interface AuditPublisher {

    /**
     * Records the event, blocking until the log has accepted it.
     *
     * @param event the event to record
     * @throws AuditWriteException when the event could not be recorded; the caller must abort
     */
    void publish(AuditEvent event);
}
