package pk.nust.cuas.alert.domain.exception;

import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import pk.nust.cuas.commons.exception.DomainException;

/** Raised when an incident is requested by an id that does not exist. Surfaced as {@code 404}. */
public class IncidentNotFoundException extends DomainException {

    public IncidentNotFoundException(UUID incidentId) {
        super("incident-not-found", HttpStatus.NOT_FOUND, "No incident with the given id exists.",
                Map.of("incidentId", incidentId.toString()), null);
    }
}
