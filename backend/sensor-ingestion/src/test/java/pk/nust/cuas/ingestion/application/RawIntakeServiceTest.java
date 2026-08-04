package pk.nust.cuas.ingestion.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pk.nust.cuas.detection.v1.DetectionRecord;
import pk.nust.cuas.detection.v1.SensorModality;
import pk.nust.cuas.ingestion.application.port.RawDetectionPublisher;
import pk.nust.cuas.ingestion.domain.service.DetectionValidator;
import pk.nust.cuas.ingestion.support.Detections;

@ExtendWith(MockitoExtension.class)
class RawIntakeServiceTest {

    private static final Instant SENSOR_TIME = Instant.parse("2026-08-04T12:00:00Z");

    @Mock
    private RawDetectionPublisher rawPublisher;

    private RawIntakeService service() {
        return new RawIntakeService(new DetectionValidator(), rawPublisher);
    }

    @Test
    void aValidDetectionIsPublishedToRawAndAccepted() {
        DetectionRecord raw = Detections.rf("rf-01", 7L, SENSOR_TIME);

        IntakeResult result = service().accept(raw);

        assertThat(result.accepted()).isTrue();
        assertThat(result.sensorSeq()).isEqualTo(7L);
        verify(rawPublisher).publish(eq("rf-01"), any());
    }

    @Test
    void anInvalidDetectionIsRejectedAndNotPublished() {
        DetectionRecord invalid = Detections.rf("rf-01", 7L, SENSOR_TIME).toBuilder()
                .setModality(SensorModality.MODALITY_UNSPECIFIED)
                .build();

        IntakeResult result = service().accept(invalid);

        assertThat(result.accepted()).isFalse();
        assertThat(result.violations()).anyMatch(v -> v.contains("modality"));
        verify(rawPublisher, never()).publish(any(), any());
    }

    @Test
    void aCorrelationIdIsStampedWhenTheRecordCarriesNone() {
        DetectionRecord raw = Detections.rf("rf-01", 7L, SENSOR_TIME); // no correlation id set

        service().accept(raw);

        ArgumentCaptor<DetectionRecord> published = ArgumentCaptor.forClass(DetectionRecord.class);
        verify(rawPublisher).publish(any(), published.capture());
        assertThat(published.getValue().getCorrelationId()).isNotBlank();
    }

    @Test
    void anExistingCorrelationIdIsPreserved() {
        DetectionRecord raw = Detections.rf("rf-01", 7L, SENSOR_TIME).toBuilder()
                .setCorrelationId("trace-abc")
                .build();

        service().accept(raw);

        ArgumentCaptor<DetectionRecord> published = ArgumentCaptor.forClass(DetectionRecord.class);
        verify(rawPublisher).publish(any(), published.capture());
        assertThat(published.getValue().getCorrelationId()).isEqualTo("trace-abc");
    }
}
