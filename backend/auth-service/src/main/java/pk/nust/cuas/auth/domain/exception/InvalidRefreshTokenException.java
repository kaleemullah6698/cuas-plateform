package pk.nust.cuas.auth.domain.exception;

import org.springframework.http.HttpStatus;
import pk.nust.cuas.commons.exception.DomainException;

/** The presented refresh token is unknown, expired, revoked, or already exchanged. */
public class InvalidRefreshTokenException extends DomainException {

    public static final String CODE = "INVALID_REFRESH_TOKEN";

    public InvalidRefreshTokenException() {
        super(CODE, HttpStatus.UNAUTHORIZED, "Invalid refresh token.");
    }
}
