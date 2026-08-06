package pk.nust.cuas.alert.domain.exception;

import java.util.Map;
import org.springframework.http.HttpStatus;
import pk.nust.cuas.commons.exception.DomainException;

/**
 * Raised when an operator tries a transition the aggregate forbids — acknowledging a resolved alert,
 * closing a closed incident. Surfaced as {@code 409 Conflict}.
 *
 * <p>{@code 409} rather than {@code 400}: the request was well-formed and would have been valid a
 * moment ago. Two operators working the same queue will produce this routinely, and the status is
 * what tells the console to refresh rather than to complain about the request.
 */
public class IllegalAlertTransitionException extends DomainException {

    public IllegalAlertTransitionException(String reference, String reason) {
        super("illegal-transition", HttpStatus.CONFLICT, reason,
                Map.of("reference", reference), null);
    }
}
