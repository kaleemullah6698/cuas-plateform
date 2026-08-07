package pk.nust.cuas.gateway.realtime;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Clock;
import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator;
import pk.nust.cuas.gateway.config.GatewayWebSocketProperties;

/**
 * The in-memory registry of open WebSocket sessions (ARCHITECTURE.md §4.1.1, §3.5).
 *
 * <p>In-memory per instance: for M9 the gateway is a single fan-out node, which is the deliberate thin
 * scope. §4.1.1's Redis-backed session registry — the one that lets several gateway instances share
 * fan-out — is a documented deferral (see ROADMAP M9). The registry enforces the §7 limit of five
 * concurrent connections per user by closing the oldest when a sixth opens, and exports the open-
 * connection count as {@code cuas_gateway_ws_connections} for the Operator-Experience dashboard.
 */
@Component
public class SessionRegistry {

    private static final Logger log = LoggerFactory.getLogger(SessionRegistry.class);
    private static final CloseStatus TOO_MANY = new CloseStatus(4001, "too many concurrent connections");

    private final Map<String, GatewaySession> byId = new ConcurrentHashMap<>();
    private final Map<String, Deque<String>> byUser = new HashMap<>();
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final int maxPerUser;

    public SessionRegistry(ObjectMapper objectMapper, Clock clock,
                           GatewayWebSocketProperties properties, MeterRegistry meterRegistry) {
        this.objectMapper = objectMapper;
        this.clock = clock;
        this.maxPerUser = properties.maxSessionsPerUser();
        Gauge.builder("cuas.gateway.ws.connections", byId, Map::size)
                .description("Open WebSocket connections")
                .register(meterRegistry);
    }

    /**
     * Registers a freshly-handshaken session, wrapping it for concurrent buffered sends and enforcing
     * the per-user limit.
     *
     * @return the managed session
     */
    public GatewaySession register(WebSocketSession raw, String userId, Set<String> permissions) {
        WebSocketSession concurrent = new ConcurrentWebSocketSessionDecorator(raw, 5000, 512 * 1024);
        GatewaySession session = new GatewaySession(concurrent, userId, permissions, clock.instant(),
                objectMapper);
        byId.put(session.id(), session);
        evictOldestBeyondLimit(userId, session.id());
        log.debug("WebSocket session {} opened for {}", session.id(), userId);
        return session;
    }

    private synchronized void evictOldestBeyondLimit(String userId, String newSessionId) {
        Deque<String> userSessions = byUser.computeIfAbsent(userId, k -> new ArrayDeque<>());
        userSessions.addLast(newSessionId);
        while (userSessions.size() > maxPerUser) {
            String oldest = userSessions.pollFirst();
            GatewaySession evicted = byId.remove(oldest);
            if (evicted != null) {
                log.debug("Closing oldest session {} for {} — over the {}-connection limit",
                        oldest, userId, maxPerUser);
                evicted.close(TOO_MANY);
            }
        }
    }

    public void remove(String sessionId, String userId) {
        byId.remove(sessionId);
        synchronized (this) {
            Deque<String> userSessions = byUser.get(userId);
            if (userSessions != null) {
                userSessions.remove(sessionId);
                if (userSessions.isEmpty()) {
                    byUser.remove(userId);
                }
            }
        }
    }

    public GatewaySession get(String sessionId) {
        return byId.get(sessionId);
    }

    public Collection<GatewaySession> all() {
        return byId.values();
    }

    /** The open sessions currently subscribed to a channel. */
    public java.util.List<GatewaySession> subscribersOf(Channel channel) {
        return byId.values().stream().filter(session -> session.isSubscribedTo(channel)).toList();
    }

    public int size() {
        return byId.size();
    }
}
