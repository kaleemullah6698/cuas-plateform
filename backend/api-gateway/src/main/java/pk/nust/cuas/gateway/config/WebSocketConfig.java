package pk.nust.cuas.gateway.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import pk.nust.cuas.gateway.api.ws.GatewayWebSocketHandler;
import pk.nust.cuas.gateway.api.ws.JwtHandshakeInterceptor;

/**
 * Registers the {@code /ws} endpoint and enables the fan-out schedulers (ARCHITECTURE.md §3.5, §4.1.1).
 *
 * <p>The handshake is authenticated by {@link JwtHandshakeInterceptor}; the security filter chain
 * permits {@code /ws} so the upgrade reaches the interceptor rather than being rejected for lacking a
 * bearer header. Allowed origins are explicit (empty = same-origin only), so a deployment that forgets
 * to configure them serves no cross-origin browser rather than all of them.
 */
@Configuration
@EnableWebSocket
@EnableScheduling
@EnableConfigurationProperties(GatewayWebSocketProperties.class)
public class WebSocketConfig implements WebSocketConfigurer {

    private final GatewayWebSocketHandler handler;
    private final JwtHandshakeInterceptor handshakeInterceptor;
    private final GatewayWebSocketProperties properties;

    public WebSocketConfig(GatewayWebSocketHandler handler,
                           JwtHandshakeInterceptor handshakeInterceptor,
                           GatewayWebSocketProperties properties) {
        this.handler = handler;
        this.handshakeInterceptor = handshakeInterceptor;
        this.properties = properties;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(handler, "/ws")
                .addInterceptors(handshakeInterceptor)
                .setAllowedOrigins(properties.allowedOrigins().toArray(String[]::new));
    }
}
