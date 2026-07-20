package pk.nust.cuas.commons.exception;

import java.net.URI;
import java.time.Instant;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import pk.nust.cuas.commons.correlation.CorrelationId;

/**
 * Builds RFC&nbsp;7807 {@link ProblemDetail} responses with the platform's standard extensions.
 *
 * <p>Every problem produced here carries a {@code correlationId} and a {@code timestamp}, so an
 * operator reading an error in the UI can trace it back through the logs of every service that
 * handled the request. A stable {@code code} is included for machine consumption.
 *
 * <p>Using Spring's built-in {@link ProblemDetail} rather than a bespoke response record keeps a
 * single RFC&nbsp;7807 implementation across the platform.
 */
public final class ProblemDetailFactory {

    /** Base URI under which problem {@code type} values are namespaced. */
    public static final String TYPE_BASE = "https://cuas.nust.edu.pk/errors/";

    private ProblemDetailFactory() {
    }

    /**
     * Builds a problem detail for a domain exception, honouring its status, code, and properties.
     *
     * @param exception the domain exception to translate
     * @return a populated problem detail
     */
    public static ProblemDetail from(DomainException exception) {
        ProblemDetail problem = create(exception.status(), exception.code(), exception.getMessage());
        exception.properties().forEach(problem::setProperty);
        return problem;
    }

    /**
     * Builds a problem detail from an explicit status, code, and detail message.
     *
     * @param status the HTTP status
     * @param code   the stable, machine-readable error code
     * @param detail the human-readable detail
     * @return a populated problem detail
     */
    public static ProblemDetail create(HttpStatusCode status, String code, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setType(URI.create(TYPE_BASE + code));
        problem.setTitle(titleFor(status, code));
        problem.setProperty("code", code);
        problem.setProperty("timestamp", Instant.now().toString());
        CorrelationId.current().ifPresent(id -> problem.setProperty("correlationId", id));
        return problem;
    }

    /**
     * Builds a problem detail carrying field-level validation errors.
     *
     * @param code   the stable, machine-readable error code
     * @param detail the human-readable summary
     * @param errors field name to message, describing each rejected value
     * @return a populated problem detail with an {@code errors} extension
     */
    public static ProblemDetail validation(String code, String detail, Map<String, String> errors) {
        ProblemDetail problem = create(HttpStatus.BAD_REQUEST, code, detail);
        problem.setProperty("errors", errors);
        return problem;
    }

    private static String titleFor(HttpStatusCode status, String code) {
        HttpStatus resolved = HttpStatus.resolve(status.value());
        return resolved != null ? resolved.getReasonPhrase() : code;
    }
}
