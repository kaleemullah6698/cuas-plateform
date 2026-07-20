package pk.nust.cuas.commons.audit;

/**
 * Signals that an audit event could not be recorded.
 *
 * <p>Per ARCHITECTURE.md section 7.5 this must abort the action being audited. It is unchecked so
 * that it propagates through service code by default: a caller has to go out of its way to
 * suppress it, and doing so is a defect.
 */
public class AuditWriteException extends RuntimeException {

    public AuditWriteException(String message, Throwable cause) {
        super(message, cause);
    }

    public AuditWriteException(String message) {
        super(message);
    }
}
