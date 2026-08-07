package pk.nust.cuas.gateway.config;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * WebSocket fan-out tuning (ARCHITECTURE.md §2.5.2, §3.5), bound from {@code gateway.ws.*}.
 *
 * @param flushIntervalMs     the track/sensor delta coalescing window
 * @param heartbeatIntervalMs the liveness beat cadence
 * @param maxSessionsPerUser  concurrent connections per user before the oldest is closed
 * @param allowedOrigins      origins permitted to open a WS; empty means none
 */
@ConfigurationProperties(prefix = "gateway.ws")
public record GatewayWebSocketProperties(@DefaultValue("250") long flushIntervalMs,
                                         @DefaultValue("5000") long heartbeatIntervalMs,
                                         @DefaultValue("5") int maxSessionsPerUser,
                                         @DefaultValue List<String> allowedOrigins) {
}
