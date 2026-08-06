package pk.nust.cuas.threat.domain.exception;

import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import pk.nust.cuas.commons.exception.DomainException;

/**
 * Raised when a track has no assessment. Surfaced as {@code 404 Not Found}.
 *
 * <p>Deliberately not answered with a zero-score assessment. "This track has never been scored" and
 * "this track scored zero" are different facts, and returning the second for the first would tell an
 * operator the system had judged a track harmless when it had not judged it at all.
 */
public class AssessmentNotFoundException extends DomainException {

    public AssessmentNotFoundException(UUID trackId) {
        super("assessment-not-found", HttpStatus.NOT_FOUND,
                "No threat assessment exists for the given track.",
                Map.of("trackId", trackId.toString()), null);
    }
}
