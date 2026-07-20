package pk.nust.cuas.user.config;

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
import pk.nust.cuas.user.infrastructure.grpc.UserGrpcService;

/**
 * Runs the gRPC server alongside the HTTP one.
 *
 * <p>Wired directly against grpc-java rather than through a third-party Spring starter: the
 * service exposes a single RPC, and a starter would add a dependency and a lifecycle model for no
 * benefit at this size.
 *
 * <p>Started on {@link ApplicationReadyEvent} so the server only accepts traffic once the
 * datasource and the rest of the context are up — otherwise the first call could arrive before the
 * repository can answer it.
 */
@Configuration
public class GrpcServerConfig implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(GrpcServerConfig.class);
    private static final int SHUTDOWN_TIMEOUT_SECONDS = 10;

    private final UserGrpcService userGrpcService;
    private final int port;
    private Server server;

    public GrpcServerConfig(UserGrpcService userGrpcService,
                            @Value("${grpc.server.port:9082}") int port) {
        this.userGrpcService = userGrpcService;
        this.port = port;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void start() throws IOException {
        server = ServerBuilder.forPort(port).addService(userGrpcService).build().start();
        log.info("gRPC server listening on port {}", port);
    }

    @Override
    public void close() throws InterruptedException {
        if (server == null) {
            return;
        }
        // Graceful first, so in-flight permission resolutions finish rather than failing a login.
        server.shutdown();
        if (!server.awaitTermination(SHUTDOWN_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
            server.shutdownNow();
        }
        log.info("gRPC server stopped");
    }
}
