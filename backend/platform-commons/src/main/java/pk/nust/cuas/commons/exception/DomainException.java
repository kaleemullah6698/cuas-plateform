package pk.nust.cuas.commons.exception;

import java.util.Map;
import org.springframework.http.HttpStatus;

/**
 * Base type for exceptions that represent a violated business rule or a failed domain operation.
 *
 * <p>Each service defines its own concrete subclasses (for example {@code TrackNotFoundException})
 * in its {@code domain} layer. This base carries the machine-readable {@link #code()}, the HTTP
 * {@link #status()} the API boundary should surface, and optional structured {@link #properties()}
 * that enrich the resulting RFC&nbsp;7807 response.
 *
 * <p>This class is deliberately <em>not</em> {@code sealed}: the hierarchy is open by design so
 * that every service can extend it. It is unchecked so that domain logic is not forced to declare
 * infrastructure-facing throws clauses across architectural boundaries.
 */
public abstract class DomainException extends RuntimeException {

    private final String code;
    private final HttpStatus status;
    private final transient Map<String, Object> properties;

    /**
     * @param code    stable, machine-readable identifier for this error condition
     * @param status  HTTP status the API boundary should return for this condition
     * @param message human-readable description safe to expose to an authenticated caller
     */
    protected DomainException(String code, HttpStatus status, String message) {
        this(code, status, message, Map.of(), null);
    }

    /**
     * @param code       stable, machine-readable identifier for this error condition
     * @param status     HTTP status the API boundary should return for this condition
     * @param message    human-readable description safe to expose to an authenticated caller
     * @param properties additional structured detail to attach to the problem response
     * @param cause      the underlying cause, or {@code null}
     */
    protected DomainException(String code, HttpStatus status, String message,
                              Map<String, Object> properties, Throwable cause) {
        super(message, cause);
        this.code = code;
        this.status = status;
        this.properties = Map.copyOf(properties);
    }

    /** @return the stable, machine-readable error code */
    public String code() {
        return code;
    }

    /** @return the HTTP status the API boundary should return */
    public HttpStatus status() {
        return status;
    }

    /** @return additional structured detail for the problem response; never {@code null} */
    public Map<String, Object> properties() {
        return properties;
    }
}
