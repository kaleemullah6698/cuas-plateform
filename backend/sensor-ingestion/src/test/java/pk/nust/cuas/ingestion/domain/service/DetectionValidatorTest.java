package pk.nust.cuas.ingestion.domain.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import pk.nust.cuas.ingestion.domain.model.Modality;
import pk.nust.cuas.ingestion.domain.model.RawDetection;

class DetectionValidatorTest {

    private final DetectionValidator validator = new DetectionValidator();

    private RawDetection valid() {
        return new RawDetection("rf-01", Modality.RF, Optional.of(Instant.now()), 1L, 0.9f, "corr");
    }

    @Test
    void aWellFormedHeaderHasNoViolations() {
        assertThat(validator.validate(valid())).isEmpty();
    }

    @Test
    void aBlankSensorIdIsRejected() {
        RawDetection detection = new RawDetection("  ", Modality.RF, Optional.of(Instant.now()), 1L, 0.9f, "");
        assertThat(validator.validate(detection)).anyMatch(v -> v.contains("sensorId"));
    }

    @Test
    void anUnspecifiedModalityIsRejected() {
        RawDetection detection =
                new RawDetection("rf-01", Modality.UNSPECIFIED, Optional.of(Instant.now()), 1L, 0.9f, "");
        assertThat(validator.validate(detection)).anyMatch(v -> v.contains("modality"));
    }

    @Test
    void aMissingSensorTimeIsRejected() {
        RawDetection detection = new RawDetection("rf-01", Modality.RF, Optional.empty(), 1L, 0.9f, "");
        assertThat(validator.validate(detection)).anyMatch(v -> v.contains("sensorTime"));
    }

    @Test
    void aQualityOutsideTheUnitIntervalIsRejected() {
        RawDetection tooHigh = new RawDetection("rf-01", Modality.RF, Optional.of(Instant.now()), 1L, 1.4f, "");
        RawDetection negative = new RawDetection("rf-01", Modality.RF, Optional.of(Instant.now()), 1L, -0.1f, "");

        assertThat(validator.validate(tooHigh)).anyMatch(v -> v.contains("quality"));
        assertThat(validator.validate(negative)).anyMatch(v -> v.contains("quality"));
    }

    @Test
    void everyViolationIsReportedAtOnce() {
        RawDetection detection = new RawDetection("", Modality.UNSPECIFIED, Optional.empty(), 1L, 5.0f, "");
        assertThat(validator.validate(detection)).hasSize(4);
    }
}
