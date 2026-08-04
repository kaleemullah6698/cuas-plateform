package pk.nust.cuas.ingestion.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Random;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pk.nust.cuas.detection.v1.DetectionRecord;
import pk.nust.cuas.detection.v1.SensorModality;
import pk.nust.cuas.ingestion.application.port.NormalizedDetectionPublisher;
import pk.nust.cuas.ingestion.domain.exception.IngestionUnavailableException;
import pk.nust.cuas.ingestion.domain.model.Modality;
import pk.nust.cuas.ingestion.domain.model.NormalizationOutcome;
import pk.nust.cuas.ingestion.domain.port.ClockDriftStore;
import pk.nust.cuas.ingestion.domain.port.DedupWindow;
import pk.nust.cuas.ingestion.domain.port.SensorStateStore;
import pk.nust.cuas.ingestion.domain.service.ClockDriftEstimator;
import pk.nust.cuas.ingestion.domain.service.DetectionValidator;
import pk.nust.cuas.commons.id.Uuidv7Generator;
import pk.nust.cuas.ingestion.support.Detections;

@ExtendWith(MockitoExtension.class)
class NormalizationServiceTest {

    private static final Instant SENSOR_TIME = Instant.parse("2026-08-04T12:00:00Z");
    private static final Instant INGEST_TIME = Instant.parse("2026-08-04T12:00:01Z"); // +1000 ms

    @Mock
    private DedupWindow dedupWindow;
    @Mock
    private ClockDriftStore driftStore;
    @Mock
    private SensorStateStore stateStore;
    @Mock
    private NormalizedDetectionPublisher normalizedPublisher;

    private NormalizationService service() {
        return new NormalizationService(
                new DetectionValidator(),
                dedupWindow,
                driftStore,
                new ClockDriftEstimator(1.0d), // take the sample whole, for a predictable drift
                new Uuidv7Generator(new Random(7)),
                normalizedPublisher,
                stateStore,
                Clock.fixed(INGEST_TIME, ZoneOffset.UTC));
    }

    @Test
    void aValidDetectionIsNormalisedPublishedAndRecorded() {
        DetectionRecord raw = Detections.rf("rf-01", 42L, SENSOR_TIME);

        NormalizationOutcome outcome = service().normalize(raw);

        assertThat(outcome).isInstanceOf(NormalizationOutcome.Normalized.class);
        ArgumentCaptor<DetectionRecord> published = ArgumentCaptor.forClass(DetectionRecord.class);
        verify(normalizedPublisher).publish(eq("rf-01"), published.capture());

        DetectionRecord normalized = published.getValue();
        assertThat(normalized.getDetectionId()).isNotBlank();
        assertThat(normalized.getIngestTime().getSeconds()).isEqualTo(INGEST_TIME.getEpochSecond());
        assertThat(normalized.getSchemaVersion()).isEqualTo("1.0.0");
        // The sensor's own clock reading is preserved unchanged (section 5.3).
        assertThat(normalized.getSensorTime()).isEqualTo(Detections.timestamp(SENSOR_TIME));
        verify(stateStore).recordAccepted(eq("rf-01"), eq(Modality.RF), eq(INGEST_TIME), anyLong());
    }

    @Test
    void clockDriftIsMeasuredAsIngestMinusSensorTimeAndPersisted() {
        DetectionRecord raw = Detections.rf("rf-01", 1L, SENSOR_TIME);

        NormalizationOutcome outcome = service().normalize(raw);

        // alpha=1.0 and no prior estimate, so the drift is the raw sample: 1000 ms.
        assertThat(outcome).isInstanceOfSatisfying(NormalizationOutcome.Normalized.class,
                normalized -> assertThat(normalized.clockDriftMs()).isEqualTo(1000L));
        verify(driftStore).update("rf-01", 1000L);
    }

    @Test
    void aDuplicateIsDroppedNotPublished() {
        when(dedupWindow.seenBefore("rf-01", 42L)).thenReturn(true);

        NormalizationOutcome outcome = service().normalize(Detections.rf("rf-01", 42L, SENSOR_TIME));

        assertThat(outcome).isInstanceOf(NormalizationOutcome.Duplicate.class);
        verify(normalizedPublisher, never()).publish(any(), any());
        verify(dedupWindow, never()).markSeen(any(), anyLong());
        verify(stateStore).recordDuplicate("rf-01", Modality.RF);
    }

    @Test
    void anInvalidDetectionIsRecordedAndNotPublished() {
        DetectionRecord invalid = Detections.rf("rf-01", 1L, SENSOR_TIME).toBuilder()
                .setModality(SensorModality.MODALITY_UNSPECIFIED)
                .build();

        NormalizationOutcome outcome = service().normalize(invalid);

        assertThat(outcome).isInstanceOf(NormalizationOutcome.Invalid.class);
        verify(normalizedPublisher, never()).publish(any(), any());
        verify(stateStore).recordInvalid("rf-01", Modality.UNSPECIFIED);
    }

    @Test
    void theDedupMarkIsWrittenOnlyAfterASuccessfulPublish() {
        doThrow(new IngestionUnavailableException("broker down", null))
                .when(normalizedPublisher).publish(any(), any());

        assertThatThrownBy(() -> service().normalize(Detections.rf("rf-01", 9L, SENSOR_TIME)))
                .isInstanceOf(IngestionUnavailableException.class);

        // A failed publish must leave the record unmarked so the redelivery is retried, not dropped.
        verify(dedupWindow, never()).markSeen(any(), anyLong());
        verify(stateStore, never()).recordAccepted(any(), any(), any(), anyLong());
    }

    @Test
    void aValidDetectionMarksTheDedupWindowAfterPublishing() {
        service().normalize(Detections.rf("rf-01", 5L, SENSOR_TIME));

        verify(normalizedPublisher).publish(eq("rf-01"), any());
        verify(dedupWindow).markSeen("rf-01", 5L);
    }
}
