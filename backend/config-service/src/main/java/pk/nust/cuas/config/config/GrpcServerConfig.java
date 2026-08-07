package pk.nust.cuas.config.config;

import io.grpc.Server;
import io.grpc.ServerBuilder;
import java.io.IOException;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;
import pk.nust.cuas.config.api.grpc.WatchConfigGrpcService;

/**
 * Runs the gRPC server that carries the {@code WatchConfig} stream (ARCHITECTURE.md §4.1.12).
 *
 * <p>Wired directly against grpc-java like the platform's other gRPC surfaces — one service does not
 * justify a starter's lifecycle model. Started on {@link ApplicationReadyEvent} so no subscriber
 * arrives before the repository is up, and shut down gracefully so open streams end cleanly.
 *
 * <p>Disable with {@code grpc.server.enabled=false} — the sliced tests that exercise the REST surface
 * or the repository do not need a listening port.
 */
@Configuration
@ConditionalOnProperty(name = "grpc.server.enabled", havingValue = "true", matchIfMissing = true)
public class GrpcServerConfig implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(GrpcServerConfig.class);
    private static final int SHUTDOWN_TIMEOUT_SECONDS = 10;

    private final WatchConfigGrpcService watchConfigService;
    private final int port;
    private Server server;

    public GrpcServerConfig(WatchConfigGrpcService watchConfigService,
                            @Value("${grpc.server.port:9091}") int port) {
        this.watchConfigService = watchConfigService;
        this.port = port;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void start() throws IOException {
        server = ServerBuilder.forPort(port).addService(watchConfigService).build().start();
        log.info("Config gRPC server listening on port {}", server.getPort());
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
        log.info("Config gRPC server stopped");
    }
}
