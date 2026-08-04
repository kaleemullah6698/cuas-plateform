package pk.nust.cuas.simulator.client;

import io.grpc.Channel;
import io.grpc.stub.StreamObserver;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pk.nust.cuas.detection.v1.DetectionRecord;
import pk.nust.cuas.ingestion.v1.DetectionIngestServiceGrpc;
import pk.nust.cuas.ingestion.v1.IngestAck;
import pk.nust.cuas.simulator.config.Transport;

/**
 * Drives the real gRPC intake, {@code DetectionIngestService.StreamDetections} (ARCHITECTURE.md
 * sections 4.1.4, 5.3.1).
 *
 * <p>Opens one bidirectional stream, sends every detection on it, and counts the per-record acks the
 * server streams back — exactly the exchange a real streaming sensor adapter has. The call blocks
 * until the server completes the response stream so the returned report is final.
 */
public class GrpcIngestClient implements IngestClient {

    private static final Logger log = LoggerFactory.getLogger(GrpcIngestClient.class);
    private static final long STREAM_TIMEOUT_SECONDS = 60;

    private final Channel channel;

    public GrpcIngestClient(Channel channel) {
        this.channel = channel;
    }

    @Override
    public Transport transport() {
        return Transport.GRPC;
    }

    @Override
    public IngestReport send(List<DetectionRecord> detections) {
        DetectionIngestServiceGrpc.DetectionIngestServiceStub stub =
                DetectionIngestServiceGrpc.newStub(channel);

        CountDownLatch completed = new CountDownLatch(1);
        AtomicInteger accepted = new AtomicInteger();
        AtomicInteger rejected = new AtomicInteger();
        AtomicReference<Throwable> failure = new AtomicReference<>();

        StreamObserver<DetectionRecord> requestStream = stub.streamDetections(new StreamObserver<>() {
            @Override
            public void onNext(IngestAck ack) {
                (ack.getAccepted() ? accepted : rejected).incrementAndGet();
            }

            @Override
            public void onError(Throwable error) {
                failure.set(error);
                completed.countDown();
            }

            @Override
            public void onCompleted() {
                completed.countDown();
            }
        });

        try {
            for (DetectionRecord detection : detections) {
                requestStream.onNext(detection);
            }
            requestStream.onCompleted();
            if (!completed.await(STREAM_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                throw new IllegalStateException("gRPC intake did not complete within the timeout");
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("interrupted while streaming detections", ex);
        }

        if (failure.get() != null) {
            throw new IllegalStateException("gRPC intake stream failed", failure.get());
        }
        log.info("gRPC intake: sent {}, accepted {}, rejected {}",
                detections.size(), accepted.get(), rejected.get());
        return new IngestReport(Transport.GRPC, detections.size(), accepted.get(), rejected.get());
    }
}
