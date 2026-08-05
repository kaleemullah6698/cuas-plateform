package pk.nust.cuas.track.config;

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
import pk.nust.cuas.track.api.grpc.TrackStreamGrpcService;

/**
 * Runs the gRPC server for {@code TrackService.SubscribeTracks} alongside the HTTP one
 * (ARCHITECTURE.md section 4.1.5).
 *
 * <p>Wired directly against grpc-java rather than through a starter, matching sensor-ingestion and
 * user-service: one streaming RPC does not justify a starter's dependency and lifecycle model.
 *
 * <p><b>Authorisation is transport-level, per section 7.2.</b> {@code Service ↔ Service} is mTLS with
 * SPIFFE-style workload identity, certificate SANs pinning the caller — the same protection the
 * existing gRPC surfaces in user-service and sensor-ingestion rely on, and the reason none of them
 * carries {@code @PreAuthorize}. The {@code track:read} permission of section 7.4 is a *user*
 * permission: the gateway holds the user's JWT, enforces it, and fans tracks out over its WebSocket
 * (section 3.5). Accepting a user token on this internal stream would create a second, weaker path to
 * data the gateway already guards. Certificates are a deployment concern (internal CA, 24-hour TTL,
 * section 7.2); this server binds the port and the platform's mTLS configuration terminates it.
 *
 * <p>Started on {@link ApplicationReadyEvent} so no subscriber attaches before the context is up, and
 * shut down gracefully so in-flight events are flushed rather than cut off.
 */
@Configuration
public class GrpcServerConfig implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(GrpcServerConfig.class);
    private static final int SHUTDOWN_TIMEOUT_SECONDS = 10;

    private final TrackStreamGrpcService trackStreamService;
    private final int port;
    private Server server;

    public GrpcServerConfig(TrackStreamGrpcService trackStreamService,
                            @Value("${grpc.server.port:9084}") int port) {
        this.trackStreamService = trackStreamService;
        this.port = port;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void start() throws IOException {
        server = ServerBuilder.forPort(port).addService(trackStreamService).build().start();
        log.info("gRPC server listening on port {}", port);
    }

    /** @return the port the server actually bound, for a test that asked for an ephemeral one */
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
