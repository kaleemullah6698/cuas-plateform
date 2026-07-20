package pk.nust.cuas.user.domain.exception;

import org.springframework.http.HttpStatus;
import pk.nust.cuas.commons.exception.DomainException;

/**
 * A change would produce a permission combination section 7.4 forbids.
 */
public class SeparationOfDutyViolationException extends DomainException {

    public static final String CODE = "SEPARATION_OF_DUTY_VIOLATION";

    public SeparationOfDutyViolationException(String message) {
        super(CODE, HttpStatus.CONFLICT, message);
    }
}
