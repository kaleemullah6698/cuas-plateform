package pk.nust.cuas.audit.api.rest;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import pk.nust.cuas.commons.exception.BaseExceptionHandler;
import pk.nust.cuas.commons.exception.ProblemDetailFactory;

/**
 * Translates audit-service exceptions into RFC 7807 responses.
 *
 * <p>Inherits the platform-wide handlers from {@link BaseExceptionHandler} so errors look the same
 * across every service, and adds the two cases specific to this API.
 */
@RestControllerAdvice
public class AuditExceptionHandler extends BaseExceptionHandler {

    /** An invalid verification range and similar argument faults are client errors, not 500s. */
    @ExceptionHandler(IllegalArgumentException.class)
    public ProblemDetail onIllegalArgument(IllegalArgumentException ex) {
        return ProblemDetailFactory.create(HttpStatus.BAD_REQUEST, "invalid-argument",
                ex.getMessage());
    }

    /**
     * A permission failure. The response deliberately does not reveal which permission was
     * required — that would let a caller enumerate the authorisation model.
     */
    @ExceptionHandler(AccessDeniedException.class)
    public ProblemDetail onAccessDenied(AccessDeniedException ex) {
        return ProblemDetailFactory.create(HttpStatus.FORBIDDEN, "access-denied",
                "You do not have permission to perform this action.");
    }
}
