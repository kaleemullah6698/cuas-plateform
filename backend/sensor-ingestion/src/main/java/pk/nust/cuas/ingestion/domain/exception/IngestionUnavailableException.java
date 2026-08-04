package pk.nust.cuas.ingestion.domain.exception;

import org.springframework.http.HttpStatus;
import pk.nust.cuas.commons.exception.DomainException;

/**
 * Raised when a detection cannot be published to Kafka — the broker is unreachable or the publish
 * timed out.
 *
 * <p>Surfaced as {@code 503 Service Unavailable}: the request was well-formed and the fault is the
 * platform's, so the caller should retry rather than treat the detection as rejected. The sensor
 * adapter is expected to buffer and retry (section 7.8, the {@code /ingest/detections} row).
 */
public class IngestionUnavailableException extends DomainException {

    public IngestionUnavailableException(String message, Throwable cause) {
        super("ingestion-unavailable", HttpStatus.SERVICE_UNAVAILABLE, message,
                java.util.Map.of(), cause);
    }
}
