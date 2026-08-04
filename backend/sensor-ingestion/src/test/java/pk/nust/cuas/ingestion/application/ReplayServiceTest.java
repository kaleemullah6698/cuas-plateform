package pk.nust.cuas.ingestion.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.protobuf.util.JsonFormat;
import java.io.BufferedReader;
import java.io.StringReader;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.Mockito;
import pk.nust.cuas.detection.v1.DetectionRecord;
import pk.nust.cuas.ingestion.api.dto.ReplaySummary;
import pk.nust.cuas.ingestion.support.Detections;

@ExtendWith(MockitoExtension.class)
class ReplayServiceTest {

    private static final Instant SENSOR_TIME = Instant.parse("2026-08-04T12:00:00Z");

    @Mock
    private RawIntakeService intake;

    private ReplayService service() {
        return new ReplayService(new DetectionJsonParser(), intake);
    }

    private String line(long seq) {
        try {
            return JsonFormat.printer().omittingInsignificantWhitespace()
                    .print(Detections.rf("rf-01", seq, SENSOR_TIME));
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }

    @Test
    void countsAcceptedAndRejectedAcrossTheDataset() throws Exception {
        when(intake.accept(any())).thenReturn(new IntakeResult(true, 0L, List.of()));

        // Two valid detections, one malformed line, and a blank line that must be skipped.
        String dataset = line(1) + "\n" + line(2) + "\n{ not json }\n\n";

        ReplaySummary summary = service().replay(new BufferedReader(new StringReader(dataset)));

        assertThat(summary.total()).isEqualTo(3);
        assertThat(summary.accepted()).isEqualTo(2);
        assertThat(summary.rejected()).isEqualTo(1);
        verify(intake, Mockito.times(2)).accept(any(DetectionRecord.class));
    }

    @Test
    void anInvalidButWellFormedDetectionIsCountedRejected() throws Exception {
        // Parses fine, but the intake rejects it (e.g. failed validation).
        when(intake.accept(any())).thenReturn(new IntakeResult(false, 1L, List.of("modality is required")));

        ReplaySummary summary = service().replay(new BufferedReader(new StringReader(line(1))));

        assertThat(summary.total()).isEqualTo(1);
        assertThat(summary.accepted()).isZero();
        assertThat(summary.rejected()).isEqualTo(1);
    }

    @Test
    void anEmptyDatasetIsAValidNoOp() throws Exception {
        ReplaySummary summary = service().replay(new BufferedReader(new StringReader("\n\n")));

        assertThat(summary.total()).isZero();
        assertThat(summary.accepted()).isZero();
        assertThat(summary.rejected()).isZero();
    }
}
