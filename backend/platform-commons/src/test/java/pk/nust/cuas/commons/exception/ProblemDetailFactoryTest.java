package pk.nust.cuas.commons.exception;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import pk.nust.cuas.commons.correlation.CorrelationId;

class ProblemDetailFactoryTest {

    @AfterEach
    void clearMdc() {
        CorrelationId.clear();
    }

    @Test
    void createPopulatesStandardFields() {
        ProblemDetail problem = ProblemDetailFactory.create(
                HttpStatus.NOT_FOUND, "track-not-found", "No such track");

        assertThat(problem.getStatus()).isEqualTo(HttpStatus.NOT_FOUND.value());
        assertThat(problem.getDetail()).isEqualTo("No such track");
        assertThat(problem.getType())
                .isEqualTo(URI.create("https://cuas.nust.edu.pk/errors/track-not-found"));
        assertThat(problem.getTitle()).isEqualTo("Not Found");
        assertThat(problem.getProperties()).containsEntry("code", "track-not-found");
        assertThat(problem.getProperties()).containsKey("timestamp");
    }

    @Test
    void createIncludesCorrelationIdWhenBound() {
        CorrelationId.set("corr-42");

        ProblemDetail problem = ProblemDetailFactory.create(
                HttpStatus.BAD_REQUEST, "bad", "bad");

        assertThat(problem.getProperties()).containsEntry("correlationId", "corr-42");
    }

    @Test
    void createOmitsCorrelationIdWhenAbsent() {
        ProblemDetail problem = ProblemDetailFactory.create(
                HttpStatus.BAD_REQUEST, "bad", "bad");

        assertThat(problem.getProperties()).doesNotContainKey("correlationId");
    }

    @Test
    void fromDomainExceptionUsesStatusCodeAndProperties() {
        var ex = new TestDomainException(
                "track-not-found", HttpStatus.NOT_FOUND, "No such track",
                Map.of("trackId", "TRK-1"));

        ProblemDetail problem = ProblemDetailFactory.from(ex);

        assertThat(problem.getStatus()).isEqualTo(404);
        assertThat(problem.getProperties())
                .containsEntry("code", "track-not-found")
                .containsEntry("trackId", "TRK-1");
    }

    @Test
    void validationAttachesFieldErrors() {
        ProblemDetail problem = ProblemDetailFactory.validation(
                "validation-failed", "Request validation failed",
                Map.of("sensorId", "must not be blank"));

        assertThat(problem.getStatus()).isEqualTo(400);
        assertThat(problem.getProperties()).containsKey("errors");
        @SuppressWarnings("unchecked")
        Map<String, String> errors = (Map<String, String>) problem.getProperties().get("errors");
        assertThat(errors).containsEntry("sensorId", "must not be blank");
    }
}
