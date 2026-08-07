package pk.nust.cuas.config.api.rest;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import pk.nust.cuas.commons.exception.BaseExceptionHandler;
import pk.nust.cuas.commons.exception.ProblemDetailFactory;
import pk.nust.cuas.config.application.ConfigNotFoundException;

/**
 * Renders config-service errors as RFC 7807, inheriting the platform-wide handlers and adding the
 * four this API needs.
 */
@RestControllerAdvice
public class ConfigExceptionHandler extends BaseExceptionHandler {

    @ExceptionHandler(AccessDeniedException.class)
    public ProblemDetail onAccessDenied(AccessDeniedException ex) {
        // Does not name the missing permission — that would let a caller map the authorisation model.
        return ProblemDetailFactory.create(HttpStatus.FORBIDDEN, "access-denied",
                "You do not have permission to perform this action.");
    }

    @ExceptionHandler(ConfigNotFoundException.class)
    public ProblemDetail onNotFound(ConfigNotFoundException ex) {
        return ProblemDetailFactory.create(HttpStatus.NOT_FOUND, "not-found", ex.getMessage());
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ProblemDetail onTypeMismatch(MethodArgumentTypeMismatchException ex) {
        return ProblemDetailFactory.create(HttpStatus.BAD_REQUEST, "invalid-parameter",
                "The parameter '" + ex.getName() + "' has an invalid value.");
    }

    /**
     * An invalid configuration value, or a kind that contradicts the namespace's existing kind. The
     * message describes the caller's own request and is safe to return.
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ProblemDetail onInvalidRequest(IllegalArgumentException ex) {
        return ProblemDetailFactory.create(HttpStatus.BAD_REQUEST, "invalid-request", ex.getMessage());
    }
}
