package pk.nust.cuas.ingestion.api.grpc;

import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import pk.nust.cuas.commons.correlation.CorrelationId;
import pk.nust.cuas.detection.v1.DetectionRecord;
import pk.nust.cuas.ingestion.application.IntakeResult;
import pk.nust.cuas.ingestion.application.RawIntakeService;
import pk.nust.cuas.ingestion.domain.exception.IngestionUnavailableException;
import pk.nust.cuas.ingestion.v1.DetectionIngestServiceGrpc;
import pk.nust.cuas.ingestion.v1.IngestAck;

/**
 * The bidirectional gRPC intake, {@code DetectionIngestService.StreamDetections} (ARCHITECTURE.md
 * sections 4.1.4, 5.3.1).
 *
 * <p>An adapter streams {@link DetectionRecord}s and receives one {@link IngestAck} per record, so
 * back-pressure and per-record acceptance are visible without tearing down the stream. Validation
 * and publication reuse the same {@link RawIntakeService} the REST endpoint uses, so a detection is
 * treated identically however it arrives.
 *
 * <p>A rejected record is a normal per-record ack with {@code accepted=false}; it does not end the
 * stream. A broker outage is different — it is not the record's fault and the adapter should retry —
 * so it terminates the stream with {@code UNAVAILABLE} and stops acking. Like the REST endpoint,
 * this surface carries no RBAC: sensors are not users (M4 decision).
 */
@Component
public class DetectionIngestGrpcService extends DetectionIngestServiceGrpc.DetectionIngestServiceImplBase {

    private static final Logger log = LoggerFactory.getLogger(DetectionIngestGrpcService.class);

    private final RawIntakeService intake;

    public DetectionIngestGrpcService(RawIntakeService intake) {
        this.intake = intake;
    }

    @Override
    public StreamObserver<DetectionRecord> streamDetections(StreamObserver<IngestAck> responses) {
        return new StreamObserver<>() {
            private final AtomicBoolean terminated = new AtomicBoolean(false);

            @Override
            public void onNext(DetectionRecord record) {
                if (terminated.get()) {
                    return;
                }
                CorrelationId.set(correlationId(record));
                try {
                    IntakeResult result = intake.accept(record);
                    responses.onNext(IngestAck.newBuilder()
                            .setSensorSeq(record.getSensorSeq())
                            .setAccepted(result.accepted())
                            .build());
                } catch (IngestionUnavailableException ex) {
                    if (terminated.compareAndSet(false, true)) {
                        log.warn("Ending detection stream: ingestion is unavailable", ex);
                        responses.onError(Status.UNAVAILABLE
                                .withDescription("ingestion unavailable; retry")
                                .asRuntimeException());
                    }
                } finally {
                    CorrelationId.clear();
                }
            }

            @Override
            public void onError(Throwable error) {
                // The adapter cancelled or the connection dropped; nothing to publish, just note it.
                log.debug("Detection stream closed by client with error", error);
            }

            @Override
            public void onCompleted() {
                if (terminated.compareAndSet(false, true)) {
                    responses.onCompleted();
                }
            }
        };
    }

    private String correlationId(DetectionRecord record) {
        return record.getCorrelationId().isBlank()
                ? CorrelationId.generate()
                : record.getCorrelationId();
    }
}
