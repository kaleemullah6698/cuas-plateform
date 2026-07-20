package pk.nust.cuas.auth.domain.exception;

import org.springframework.http.HttpStatus;
import pk.nust.cuas.commons.exception.DomainException;

/** The presented MFA code was wrong, expired, or already used. */
public class MfaVerificationFailedException extends DomainException {

    public static final String CODE = "MFA_VERIFICATION_FAILED";

    public MfaVerificationFailedException() {
        super(CODE, HttpStatus.UNAUTHORIZED, "Invalid or expired verification code.");
    }
}
