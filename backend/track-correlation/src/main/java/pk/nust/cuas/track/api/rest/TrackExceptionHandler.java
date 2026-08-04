package pk.nust.cuas.track.api.rest;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import pk.nust.cuas.commons.exception.BaseExceptionHandler;
import pk.nust.cuas.commons.exception.ProblemDetailFactory;

/**
 * Renders track-correlation errors as RFC 7807, inheriting the platform-wide handlers and adding the
 * two this API needs: a forbidden read and a malformed query parameter (an unknown status value or a
 * non-UUID id in the path).
 */
@RestControllerAdvice
public class TrackExceptionHandler extends BaseExceptionHandler {

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
}
