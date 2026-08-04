package pk.nust.cuas.ingestion.domain.exception;

import java.util.Map;
import org.springframework.http.HttpStatus;
import pk.nust.cuas.commons.exception.DomainException;

/**
 * Raised when an ingest batch exceeds the configured maximum size.
 *
 * <p>Surfaced as {@code 413 Payload Too Large}: the batch itself is the problem, so the adapter
 * should split it and resend rather than retry it unchanged. The limit is reported so the adapter
 * can size subsequent batches correctly.
 */
public class BatchTooLargeException extends DomainException {

    public BatchTooLargeException(int size, int maxSize) {
        super("batch-too-large", HttpStatus.PAYLOAD_TOO_LARGE,
                "The batch of " + size + " detections exceeds the maximum of " + maxSize + ".",
                Map.of("maxBatchSize", maxSize), null);
    }
}
