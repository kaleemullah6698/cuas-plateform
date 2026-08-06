package pk.nust.cuas.alert.api.rest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import pk.nust.cuas.commons.audit.AuditWriteException;
import pk.nust.cuas.commons.correlation.CorrelationId;
import pk.nust.cuas.commons.exception.BaseExceptionHandler;
import pk.nust.cuas.commons.exception.ProblemDetailFactory;

/**
 * Renders alert-service errors as RFC 7807, inheriting the platform-wide handlers and adding the
 * four this API needs.
 */
@RestControllerAdvice
public class AlertExceptionHandler extends BaseExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(AlertExceptionHandler.class);

    @ExceptionHandler(AccessDeniedException.class)
    public ProblemDetail onAccessDenied(AccessDeniedException ex) {
        // Does not name the missing permission: that would let a caller map the authorisation model.
        return ProblemDetailFactory.create(HttpStatus.FORBIDDEN, "access-denied",
                "You do not have permission to perform this action.");
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ProblemDetail onTypeMismatch(MethodArgumentTypeMismatchException ex) {
        return ProblemDetailFactory.create(HttpStatus.BAD_REQUEST, "invalid-parameter",
                "The parameter '" + ex.getName() + "' has an invalid value.");
    }

    /**
     * An unknown disposition, evidence kind, or an evidence object that failed verification. The
     * message is safe to return: it describes the caller's own request.
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ProblemDetail onInvalidRequest(IllegalArgumentException ex) {
        return ProblemDetailFactory.create(HttpStatus.BAD_REQUEST, "invalid-request",
                ex.getMessage());
    }

    /**
     * An incident declaration or closure that could not be audited did not happen (section 7.5).
     * Reported as {@code 503} rather than {@code 500}: the request was valid and should be retried
     * once the audit log is reachable, which a {@code 500} would not communicate.
     */
    @ExceptionHandler(AuditWriteException.class)
    public ProblemDetail onAuditUnavailable(AuditWriteException ex) {
        log.error("Refusing an incident change because it could not be audited [{}]",
                CorrelationId.currentOrNew(), ex);
        return ProblemDetailFactory.create(HttpStatus.SERVICE_UNAVAILABLE, "audit-unavailable",
                "The change was not applied because it could not be recorded in the audit log.");
    }
}
