package pk.nust.cuas.threat.api.rest;

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
 * Renders threat-assessment errors as RFC 7807, inheriting the platform-wide handlers and adding the
 * three this API needs.
 */
@RestControllerAdvice
public class ThreatExceptionHandler extends BaseExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ThreatExceptionHandler.class);

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
     * A policy change that could not be audited is a policy change that did not happen (section
     * 7.5). Reported as {@code 503} rather than {@code 500}: the request was valid and would have
     * succeeded, and the caller should retry it once the audit log is reachable — a {@code 500}
     * would invite them to treat it as a bug in the request instead.
     */
    @ExceptionHandler(AuditWriteException.class)
    public ProblemDetail onAuditUnavailable(AuditWriteException ex) {
        log.error("Refusing a policy change because it could not be audited [{}]",
                CorrelationId.currentOrNew(), ex);
        return ProblemDetailFactory.create(HttpStatus.SERVICE_UNAVAILABLE, "audit-unavailable",
                "The change was not applied because it could not be recorded in the audit log.");
    }

    /**
     * A domain invariant expressed by a constructor — an unknown factor name, a threshold ordering,
     * weights that do not sum to 1.0. The message is safe to return: it describes the caller's own
     * submitted policy.
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ProblemDetail onInvalidPolicyShape(IllegalArgumentException ex) {
        return ProblemDetailFactory.create(HttpStatus.UNPROCESSABLE_ENTITY, "invalid-policy",
                ex.getMessage());
    }
}
