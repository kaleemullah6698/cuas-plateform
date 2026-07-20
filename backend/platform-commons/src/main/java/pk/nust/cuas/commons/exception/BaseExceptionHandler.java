package pk.nust.cuas.commons.exception;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;

/**
 * Base {@code @RestControllerAdvice} translating exceptions into RFC&nbsp;7807 responses.
 *
 * <p>Services extend this class with their own {@code @RestControllerAdvice}-annotated advice so
 * that the whole platform renders errors identically. It handles the exception types that every
 * service shares — bean-validation failures, constraint violations, the domain hierarchy, and an
 * unexpected fallback — while leaving services free to add handlers for their own types.
 *
 * <p>The fallback deliberately does not leak internal detail (no stack trace, no exception class,
 * no SQL) to the caller; the detail is logged server-side against the correlation ID instead.
 */
public abstract class BaseExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(BaseExceptionHandler.class);

    /** Translates a violated business rule using the exception's own status and code. */
    @ExceptionHandler(DomainException.class)
    public ProblemDetail onDomain(DomainException ex) {
        if (ex.status().is5xxServerError()) {
            log.error("Domain error [{}]: {}", ex.code(), ex.getMessage(), ex);
        } else {
            log.warn("Domain error [{}]: {}", ex.code(), ex.getMessage());
        }
        return ProblemDetailFactory.from(ex);
    }

    /** Translates {@code @Valid} request-body failures into a field-keyed validation problem. */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail onMethodArgumentNotValid(MethodArgumentNotValidException ex) {
        Map<String, String> errors = new LinkedHashMap<>();
        for (FieldError fieldError : ex.getBindingResult().getFieldErrors()) {
            errors.putIfAbsent(fieldError.getField(), messageOf(fieldError));
        }
        log.warn("Validation failed: {}", errors);
        return ProblemDetailFactory.validation("validation-failed", "Request validation failed", errors);
    }

    /** Translates constraint violations on path or query parameters. */
    @ExceptionHandler(ConstraintViolationException.class)
    public ProblemDetail onConstraintViolation(ConstraintViolationException ex) {
        Map<String, String> errors = new LinkedHashMap<>();
        for (ConstraintViolation<?> violation : ex.getConstraintViolations()) {
            errors.putIfAbsent(violation.getPropertyPath().toString(), violation.getMessage());
        }
        log.warn("Constraint violation: {}", errors);
        return ProblemDetailFactory.validation("constraint-violation", "Constraint violation", errors);
    }

    /** Last-resort handler: records the detail server-side, returns an opaque 500. */
    @ExceptionHandler(Exception.class)
    public ProblemDetail onUnexpected(Exception ex) {
        log.error("Unexpected error", ex);
        return ProblemDetailFactory.create(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "internal-error",
                "An unexpected error occurred. Reference the correlation ID when reporting this.");
    }

    private String messageOf(FieldError fieldError) {
        return fieldError.getDefaultMessage() != null ? fieldError.getDefaultMessage() : "invalid value";
    }
}
