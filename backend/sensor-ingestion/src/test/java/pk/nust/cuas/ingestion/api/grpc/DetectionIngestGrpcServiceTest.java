package pk.nust.cuas.ingestion.api.grpc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.grpc.StatusRuntimeException;
import io.grpc.stub.StreamObserver;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pk.nust.cuas.detection.v1.DetectionRecord;
import pk.nust.cuas.ingestion.application.IntakeResult;
import pk.nust.cuas.ingestion.application.RawIntakeService;
import pk.nust.cuas.ingestion.domain.exception.IngestionUnavailableException;
import pk.nust.cuas.ingestion.support.Detections;
import pk.nust.cuas.ingestion.v1.IngestAck;

@ExtendWith(MockitoExtension.class)
class DetectionIngestGrpcServiceTest {

    private static final Instant SENSOR_TIME = Instant.parse("2026-08-04T12:00:00Z");

    @Mock
    private RawIntakeService intake;
    @Mock
    private StreamObserver<IngestAck> responses;

    private StreamObserver<DetectionRecord> openStream() {
        return new DetectionIngestGrpcService(intake).streamDetections(responses);
    }

    @Test
    void anAcceptedRecordIsAckedAcceptedAndKeepsTheStreamOpen() {
        when(intake.accept(any())).thenReturn(new IntakeResult(true, 7L, List.of()));

        openStream().onNext(Detections.rf("rf-01", 7L, SENSOR_TIME));

        ArgumentCaptor<IngestAck> ack = ArgumentCaptor.forClass(IngestAck.class);
        verify(responses).onNext(ack.capture());
        assertThat(ack.getValue().getSensorSeq()).isEqualTo(7L);
        assertThat(ack.getValue().getAccepted()).isTrue();
        verify(responses, never()).onError(any());
    }

    @Test
    void aRejectedRecordIsAckedNotAcceptedWithoutEndingTheStream() {
        when(intake.accept(any())).thenReturn(new IntakeResult(false, 8L, List.of("modality is required")));

        StreamObserver<DetectionRecord> stream = openStream();
        stream.onNext(Detections.rf("rf-01", 8L, SENSOR_TIME));

        ArgumentCaptor<IngestAck> ack = ArgumentCaptor.forClass(IngestAck.class);
        verify(responses).onNext(ack.capture());
        assertThat(ack.getValue().getAccepted()).isFalse();
        verify(responses, never()).onError(any());
    }

    @Test
    void aBrokerOutageEndsTheStreamWithUnavailableAndStopsAcking() {
        when(intake.accept(any())).thenThrow(new IngestionUnavailableException("broker down", null));

        StreamObserver<DetectionRecord> stream = openStream();
        stream.onNext(Detections.rf("rf-01", 9L, SENSOR_TIME));
        // A record arriving after the stream was terminated must be ignored, not acked.
        stream.onNext(Detections.rf("rf-01", 10L, SENSOR_TIME));

        ArgumentCaptor<Throwable> error = ArgumentCaptor.forClass(Throwable.class);
        verify(responses).onError(error.capture());
        assertThat(error.getValue()).isInstanceOf(StatusRuntimeException.class);
        assertThat(((StatusRuntimeException) error.getValue()).getStatus().getCode())
                .isEqualTo(io.grpc.Status.Code.UNAVAILABLE);
        verify(responses, never()).onNext(any());
    }

    @Test
    void completingTheStreamCompletesTheResponse() {
        openStream().onCompleted();

        verify(responses).onCompleted();
    }
}
