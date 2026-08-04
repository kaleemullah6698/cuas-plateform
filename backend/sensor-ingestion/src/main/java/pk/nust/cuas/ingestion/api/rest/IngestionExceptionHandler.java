package pk.nust.cuas.ingestion.api.rest;

import org.springframework.web.bind.annotation.RestControllerAdvice;
import pk.nust.cuas.commons.exception.BaseExceptionHandler;

/**
 * Renders ingestion errors as RFC 7807 responses by inheriting the platform-wide handlers, so an
 * error from this service looks identical to one from any other.
 *
 * <p>No service-specific handlers are needed: the domain exceptions ({@code BatchTooLargeException},
 * {@code IngestionUnavailableException}) carry their own status and code and are translated by the
 * inherited {@code DomainException} handler; empty-batch and constraint failures are handled by the
 * inherited validation handlers.
 */
@RestControllerAdvice
public class IngestionExceptionHandler extends BaseExceptionHandler {
}
