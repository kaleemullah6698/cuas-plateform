package pk.nust.cuas.ingestion.config;

import io.grpc.Server;
import io.grpc.ServerBuilder;
import java.io.IOException;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;
import pk.nust.cuas.ingestion.api.grpc.DetectionIngestGrpcService;

/**
 * Runs the gRPC server for {@code DetectionIngestService.StreamDetections} alongside the HTTP one.
 *
 * <p>Wired directly against grpc-java rather than through a starter, mirroring user-service: the
 * service exposes a single streaming RPC, and a starter would add a dependency and a lifecycle
 * model for no benefit at this size. Started on {@link ApplicationReadyEvent} so the intake only
 * accepts a stream once Kafka and the rest of the context are up, and shut down gracefully so an
 * in-flight batch of acks is not cut off.
 */
@Configuration
public class GrpcServerConfig implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(GrpcServerConfig.class);
    private static final int SHUTDOWN_TIMEOUT_SECONDS = 10;

    private final DetectionIngestGrpcService ingestService;
    private final int port;
    private Server server;

    public GrpcServerConfig(DetectionIngestGrpcService ingestService,
                            @Value("${grpc.server.port:9083}") int port) {
        this.ingestService = ingestService;
        this.port = port;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void start() throws IOException {
        server = ServerBuilder.forPort(port).addService(ingestService).build().start();
        log.info("gRPC server listening on port {}", port);
    }

    @Override
    public void close() throws InterruptedException {
        if (server == null) {
            return;
        }
        server.shutdown();
        if (!server.awaitTermination(SHUTDOWN_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
            server.shutdownNow();
        }
        log.info("gRPC server stopped");
    }
}
