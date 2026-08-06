package pk.nust.cuas.notification.api.rest;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import pk.nust.cuas.commons.exception.BaseExceptionHandler;
import pk.nust.cuas.commons.exception.ProblemDetailFactory;

/**
 * Renders notification-service errors as RFC 7807, inheriting the platform-wide handlers and adding
 * the three this API needs.
 */
@RestControllerAdvice
public class NotificationExceptionHandler extends BaseExceptionHandler {

    @ExceptionHandler(AccessDeniedException.class)
    public ProblemDetail onAccessDenied(AccessDeniedException ex) {
        return ProblemDetailFactory.create(HttpStatus.FORBIDDEN, "access-denied",
                "You do not have permission to perform this action.");
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ProblemDetail onTypeMismatch(MethodArgumentTypeMismatchException ex) {
        return ProblemDetailFactory.create(HttpStatus.BAD_REQUEST, "invalid-parameter",
                "The parameter '" + ex.getName() + "' has an invalid value.");
    }

    /**
     * An invalid quiet-hours pair, an unparseable caller id, or a malformed enum. The message is safe
     * to return: it describes the caller's own request.
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ProblemDetail onInvalidRequest(IllegalArgumentException ex) {
        return ProblemDetailFactory.create(HttpStatus.BAD_REQUEST, "invalid-request", ex.getMessage());
    }

    @ExceptionHandler(NoEnabledChannelException.class)
    public ProblemDetail onNoEnabledChannel(NoEnabledChannelException ex) {
        return ProblemDetailFactory.create(HttpStatus.CONFLICT, "no-enabled-channel", ex.getMessage());
    }
}
