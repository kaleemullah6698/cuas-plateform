package pk.nust.cuas.commons.correlation;

import java.util.Optional;
import java.util.UUID;
import org.slf4j.MDC;

/**
 * Access to the correlation identifier for the current thread of execution.
 *
 * <p>A correlation ID ties together every log line, metric, and (later) trace span produced
 * while handling a single request, across service boundaries. It is stored in the SLF4J
 * {@link MDC} so that the logging layer can emit it automatically, and read back here when a
 * component needs to attach it to an outbound message or an RFC&nbsp;7807 problem response.
 *
 * <p>This class is a stateless utility over the MDC and holds no instance state.
 */
public final class CorrelationId {

    /** HTTP header carrying the correlation ID inbound and outbound. */
    public static final String HEADER = "X-Correlation-Id";

    /** MDC key under which the correlation ID is stored. */
    public static final String MDC_KEY = "correlationId";

    private CorrelationId() {
    }

    /**
     * Returns the correlation ID bound to the current thread, if any.
     *
     * @return the current correlation ID, or empty when none is bound
     */
    public static Optional<String> current() {
        return Optional.ofNullable(MDC.get(MDC_KEY));
    }

    /**
     * Returns the correlation ID bound to the current thread, or a freshly generated one.
     *
     * <p>Unlike {@link #current()} this never returns empty; use it where a value is required
     * regardless of whether a filter has already established one.
     *
     * @return the current correlation ID, or a new one when none is bound
     */
    public static String currentOrNew() {
        String existing = MDC.get(MDC_KEY);
        return existing != null ? existing : generate();
    }

    /**
     * Binds the given correlation ID to the current thread.
     *
     * @param correlationId the identifier to bind; must not be {@code null} or blank
     * @throws IllegalArgumentException when the identifier is {@code null} or blank
     */
    public static void set(String correlationId) {
        if (correlationId == null || correlationId.isBlank()) {
            throw new IllegalArgumentException("correlationId must not be null or blank");
        }
        MDC.put(MDC_KEY, correlationId);
    }

    /** Removes any correlation ID bound to the current thread. */
    public static void clear() {
        MDC.remove(MDC_KEY);
    }

    /**
     * Generates a new correlation ID.
     *
     * @return a new random identifier
     */
    public static String generate() {
        return UUID.randomUUID().toString();
    }
}
