package pk.nust.cuas.orchestrator.config;

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
import pk.nust.cuas.orchestrator.api.grpc.OrchestratorInferenceGrpcService;

/**
 * Runs the gRPC server track-correlation calls (ARCHITECTURE.md sections 4.1.5, 4.1.6).
 *
 * <p>Wired directly against grpc-java, matching sensor-ingestion, user-service and
 * track-correlation: one RPC does not justify a starter's dependency and lifecycle model.
 *
 * <p>Authorisation is transport-level per section 7.2 — {@code Service ↔ Service} is mTLS with
 * SPIFFE-style workload identity, the same protection the other inter-service gRPC surfaces rely on.
 * The orchestrator's *user-facing* surfaces are the REST endpoints, and those carry
 * {@code @PreAuthorize}.
 *
 * <p>Started on {@link ApplicationReadyEvent} so no caller arrives before the registry and providers
 * are up, and shut down gracefully so an in-flight inference completes.
 */
@Configuration
public class GrpcServerConfig implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(GrpcServerConfig.class);
    private static final int SHUTDOWN_TIMEOUT_SECONDS = 10;

    private final OrchestratorInferenceGrpcService inferenceService;
    private final int port;
    private Server server;

    public GrpcServerConfig(OrchestratorInferenceGrpcService inferenceService,
                            @Value("${grpc.server.port:9085}") int port) {
        this.inferenceService = inferenceService;
        this.port = port;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void start() throws IOException {
        server = ServerBuilder.forPort(port).addService(inferenceService).build().start();
        log.info("gRPC server listening on port {}", server.getPort());
    }

    /** @return the port actually bound, for a test that asked for an ephemeral one */
    public int boundPort() {
        return server == null ? port : server.getPort();
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
