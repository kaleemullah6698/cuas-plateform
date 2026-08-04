package pk.nust.cuas.commons.exception;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Path;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;

class BaseExceptionHandlerTest {

    /** Minimal concrete advice, standing in for a service's own. */
    private static final class TestHandler extends BaseExceptionHandler {
    }

    private final TestHandler handler = new TestHandler();

    /** Target for a synthetic {@link MethodParameter}; never invoked. */
    @SuppressWarnings("unused")
    private void sampleEndpoint(String body) {
    }

    @Test
    void onDomainUsesExceptionStatusAndCode() {
        var ex = new TestDomainException("track-not-found", HttpStatus.NOT_FOUND, "No such track");

        ProblemDetail problem = handler.onDomain(ex);

        assertThat(problem.getStatus()).isEqualTo(404);
        assertThat(problem.getProperties()).containsEntry("code", "track-not-found");
    }

    @Test
    void onMethodArgumentNotValidReturnsFieldErrors() throws NoSuchMethodException {
        BindingResult binding = new BeanPropertyBindingResult(new Object(), "request");
        binding.addError(new FieldError("request", "sensorId", "must not be blank"));
        MethodParameter parameter = new MethodParameter(
                getClass().getDeclaredMethod("sampleEndpoint", String.class), 0);
        var ex = new MethodArgumentNotValidException(parameter, binding);

        ProblemDetail problem = handler.onMethodArgumentNotValid(ex);

        assertThat(problem.getStatus()).isEqualTo(400);
        @SuppressWarnings("unchecked")
        var errors = (java.util.Map<String, String>) problem.getProperties().get("errors");
        assertThat(errors).containsEntry("sensorId", "must not be blank");
    }

    @Test
    void onConstraintViolationReturnsPropertyErrors() {
        Path path = mock(Path.class);
        when(path.toString()).thenReturn("create.sensorId");
        ConstraintViolation<?> violation = mock(ConstraintViolation.class);
        when(violation.getPropertyPath()).thenReturn(path);
        when(violation.getMessage()).thenReturn("must not be blank");
        var ex = new ConstraintViolationException(Set.of(violation));

        ProblemDetail problem = handler.onConstraintViolation(ex);

        assertThat(problem.getStatus()).isEqualTo(400);
        @SuppressWarnings("unchecked")
        var errors = (java.util.Map<String, String>) problem.getProperties().get("errors");
        assertThat(errors).containsEntry("create.sensorId", "must not be blank");
    }

    @Test
    void onUnexpectedReturnsOpaqueInternalError() {
        ProblemDetail problem = handler.onUnexpected(new RuntimeException("secret internal detail"));

        assertThat(problem.getStatus()).isEqualTo(500);
        assertThat(problem.getProperties()).containsEntry("code", "internal-error");
        // The internal message must never reach the caller.
        assertThat(problem.getDetail()).doesNotContain("secret internal detail");
    }

    @Test
    void onNoResourceReturns404NotAnOpaque500() {
        // An unmatched route must read as "not found", not "server error"; without a dedicated
        // handler the catch-all would render Spring's NoResourceFoundException as a 500.
        var ex = new org.springframework.web.servlet.resource.NoResourceFoundException(
                org.springframework.http.HttpMethod.POST, "/ingest/replay");

        ProblemDetail problem = handler.onNoResource(ex);

        assertThat(problem.getStatus()).isEqualTo(404);
        assertThat(problem.getProperties()).containsEntry("code", "not-found");
    }
}
