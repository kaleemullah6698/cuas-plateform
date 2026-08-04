package pk.nust.cuas.track.domain.exception;

import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import pk.nust.cuas.commons.exception.DomainException;

/**
 * Raised when a track is requested by an id that does not exist. Surfaced as {@code 404 Not Found}.
 */
public class TrackNotFoundException extends DomainException {

    public TrackNotFoundException(UUID trackId) {
        super("track-not-found", HttpStatus.NOT_FOUND, "No track with the given id exists.",
                Map.of("trackId", trackId.toString()), null);
    }
}
