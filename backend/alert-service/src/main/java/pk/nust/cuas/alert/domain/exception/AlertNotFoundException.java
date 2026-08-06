package pk.nust.cuas.alert.domain.exception;

import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import pk.nust.cuas.commons.exception.DomainException;

/** Raised when an alert is requested by an id that does not exist. Surfaced as {@code 404}. */
public class AlertNotFoundException extends DomainException {

    public AlertNotFoundException(UUID alertId) {
        super("alert-not-found", HttpStatus.NOT_FOUND, "No alert with the given id exists.",
                Map.of("alertId", alertId.toString()), null);
    }
}
