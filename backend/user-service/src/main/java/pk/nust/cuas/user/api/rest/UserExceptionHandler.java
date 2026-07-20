package pk.nust.cuas.user.api.rest;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import pk.nust.cuas.commons.exception.BaseExceptionHandler;
import pk.nust.cuas.commons.exception.ProblemDetailFactory;

/**
 * Translates user-service exceptions into RFC 7807 responses, inheriting the platform-wide
 * handlers so errors look identical across services.
 */
@RestControllerAdvice
public class UserExceptionHandler extends BaseExceptionHandler {

    @ExceptionHandler(AccessDeniedException.class)
    public ProblemDetail onAccessDenied(AccessDeniedException ex) {
        // Does not name the missing permission: that would let a caller map the authorisation model.
        return ProblemDetailFactory.create(HttpStatus.FORBIDDEN, "access-denied",
                "You do not have permission to perform this action.");
    }
}
