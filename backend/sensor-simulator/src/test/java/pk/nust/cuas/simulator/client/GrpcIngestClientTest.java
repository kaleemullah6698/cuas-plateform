package pk.nust.cuas.simulator.client;

import static org.assertj.core.api.Assertions.assertThat;

import io.grpc.ManagedChannel;
import io.grpc.Server;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;
import io.grpc.stub.StreamObserver;
import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import pk.nust.cuas.detection.v1.DetectionRecord;
import pk.nust.cuas.detection.v1.SensorModality;
import pk.nust.cuas.ingestion.v1.DetectionIngestServiceGrpc;
import pk.nust.cuas.ingestion.v1.IngestAck;
import pk.nust.cuas.simulator.config.Transport;
import pk.nust.cuas.simulator.generator.SimulatedDetectionFactory;

/**
 * The gRPC client against a real in-process server implementing the contract: it must stream every
 * detection and total the per-record acks the server streams back.
 */
class GrpcIngestClientTest {

    private final SimulatedDetectionFactory factory = new SimulatedDetectionFactory();
    private Server server;
    private ManagedChannel channel;

    @AfterEach
    void tearDown() {
        if (channel != null) {
            channel.shutdownNow();
        }
        if (server != null) {
            server.shutdownNow();
        }
    }

    private ManagedChannel start(DetectionIngestServiceGrpc.DetectionIngestServiceImplBase service)
            throws IOException {
        String name = InProcessServerBuilder.generateName();
        server = InProcessServerBuilder.forName(name).directExecutor().addService(service).build().start();
        channel = InProcessChannelBuilder.forName(name).directExecutor().build();
        return channel;
    }

    private List<DetectionRecord> detections(int count) {
        List<DetectionRecord> list = new ArrayList<>();
        for (long seq = 1; seq <= count; seq++) {
            list.add(factory.create("sim-rf-0", SensorModality.MODALITY_RF, seq, Instant.parse("2026-08-04T12:00:00Z")));
        }
        return list;
    }

    @Test
    void streamsEveryDetectionAndCountsAcceptedAcks() throws Exception {
        GrpcIngestClient client = new GrpcIngestClient(start(new AckService(true)));

        IngestReport report = client.send(detections(5));

        assertThat(report.transport()).isEqualTo(Transport.GRPC);
        assertThat(report.sent()).isEqualTo(5);
        assertThat(report.accepted()).isEqualTo(5);
        assertThat(report.rejected()).isZero();
    }

    @Test
    void countsRejectedAcks() throws Exception {
        GrpcIngestClient client = new GrpcIngestClient(start(new AckService(false)));

        IngestReport report = client.send(detections(3));

        assertThat(report.accepted()).isZero();
        assertThat(report.rejected()).isEqualTo(3);
    }

    /** Acks every streamed record with the configured acceptance, mirroring the real service. */
    private static final class AckService extends DetectionIngestServiceGrpc.DetectionIngestServiceImplBase {
        private final boolean accept;

        AckService(boolean accept) {
            this.accept = accept;
        }

        @Override
        public StreamObserver<DetectionRecord> streamDetections(StreamObserver<IngestAck> responses) {
            return new StreamObserver<>() {
                @Override
                public void onNext(DetectionRecord record) {
                    responses.onNext(IngestAck.newBuilder()
                            .setSensorSeq(record.getSensorSeq()).setAccepted(accept).build());
                }

                @Override
                public void onError(Throwable error) {
                }

                @Override
                public void onCompleted() {
                    responses.onCompleted();
                }
            };
        }
    }
}
