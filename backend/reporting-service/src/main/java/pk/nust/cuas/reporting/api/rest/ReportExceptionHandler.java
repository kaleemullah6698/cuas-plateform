package pk.nust.cuas.reporting.api.rest;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import pk.nust.cuas.commons.exception.BaseExceptionHandler;
import pk.nust.cuas.commons.exception.ProblemDetailFactory;

/**
 * Renders reporting-service errors as RFC 7807, inheriting the platform-wide handlers and adding the
 * ones this API needs.
 */
@RestControllerAdvice
public class ReportExceptionHandler extends BaseExceptionHandler {

    @ExceptionHandler(AccessDeniedException.class)
    public ProblemDetail onAccessDenied(AccessDeniedException ex) {
        return ProblemDetailFactory.create(HttpStatus.FORBIDDEN, "access-denied",
                "You do not have permission to perform this action.");
    }

    @ExceptionHandler(ReportNotFoundException.class)
    public ProblemDetail onNotFound(ReportNotFoundException ex) {
        return ProblemDetailFactory.create(HttpStatus.NOT_FOUND, "not-found", ex.getMessage());
    }

    @ExceptionHandler(ReportNotReadyException.class)
    public ProblemDetail onNotReady(ReportNotReadyException ex) {
        return ProblemDetailFactory.create(HttpStatus.CONFLICT, "report-not-ready", ex.getMessage());
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ProblemDetail onTypeMismatch(MethodArgumentTypeMismatchException ex) {
        return ProblemDetailFactory.create(HttpStatus.BAD_REQUEST, "invalid-parameter",
                "The parameter '" + ex.getName() + "' has an invalid value.");
    }

    /** An unknown report type or an unsupported format. The message describes the caller's own request. */
    @ExceptionHandler(IllegalArgumentException.class)
    public ProblemDetail onInvalidRequest(IllegalArgumentException ex) {
        return ProblemDetailFactory.create(HttpStatus.BAD_REQUEST, "invalid-request", ex.getMessage());
    }
}
