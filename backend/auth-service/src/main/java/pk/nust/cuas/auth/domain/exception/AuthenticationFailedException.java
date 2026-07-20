package pk.nust.cuas.auth.domain.exception;

import org.springframework.http.HttpStatus;
import pk.nust.cuas.commons.exception.DomainException;

/**
 * Authentication did not succeed.
 *
 * <p>Deliberately uniform: the same code and message are returned whether the username is unknown,
 * the password is wrong, or the account is deactivated. Distinguishing them would let an
 * unauthenticated caller enumerate valid usernames.
 */
public class AuthenticationFailedException extends DomainException {

    public static final String CODE = "AUTHENTICATION_FAILED";

    public AuthenticationFailedException() {
        super(CODE, HttpStatus.UNAUTHORIZED, "Invalid credentials.");
    }
}
