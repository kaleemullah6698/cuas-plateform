package pk.nust.cuas.threat.domain.exception;

import java.util.Map;
import org.springframework.http.HttpStatus;
import pk.nust.cuas.commons.exception.DomainException;

/**
 * Raised when a submitted policy would not be a valid one. Surfaced as {@code 422 Unprocessable
 * Entity} — the request was well-formed, but the policy it describes cannot exist.
 *
 * <p>Rejecting a bad policy at publication is the only place it can be rejected usefully. Weights
 * that do not sum to 1.0 or thresholds out of order do not fail loudly at scoring time; they produce
 * plausible-looking numbers that are quietly wrong for every track until somebody notices.
 */
public class InvalidPolicyException extends DomainException {

    public InvalidPolicyException(String reason) {
        super("invalid-policy", HttpStatus.UNPROCESSABLE_ENTITY,
                "The submitted threat policy is not valid.", Map.of("reason", reason), null);
    }
}
