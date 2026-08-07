package pk.nust.cuas.gateway.realtime;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.time.Instant;
import java.util.EnumMap;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

/**
 * One authenticated WebSocket connection (ARCHITECTURE.md §3.5): the underlying session (already
 * wrapped for concurrent, buffered sends), who it belongs to, what it may see, which channels it has
 * subscribed to, and a monotonic {@code seq} per channel.
 *
 * <p>Every write serialises the message and hands it to the decorated session, which serialises the
 * actual send — so the Kafka consumer thread, the flush scheduler and the heartbeat scheduler can all
 * write to the same connection without corrupting a frame.
 */
public final class GatewaySession {

    private static final Logger log = LoggerFactory.getLogger(GatewaySession.class);

    private final WebSocketSession session;
    private final String userId;
    private final Set<String> permissions;
    private final Instant createdAt;
    private final ObjectMapper objectMapper;
    private final Set<Channel> subscriptions = ConcurrentHashMap.newKeySet();
    private final EnumMap<Channel, AtomicLong> seqByChannel = new EnumMap<>(Channel.class);

    public GatewaySession(WebSocketSession session, String userId, Set<String> permissions,
                          Instant createdAt, ObjectMapper objectMapper) {
        this.session = session;
        this.userId = userId;
        this.permissions = Set.copyOf(permissions);
        this.createdAt = createdAt;
        this.objectMapper = objectMapper;
        for (Channel channel : Channel.values()) {
            seqByChannel.put(channel, new AtomicLong(0));
        }
    }

    public String id() {
        return session.getId();
    }

    public String userId() {
        return userId;
    }

    public Instant createdAt() {
        return createdAt;
    }

    /**
     * Whether this session may subscribe to a channel. Tracks, sensors and system are visible to any
     * authenticated operator (VIEWER); the alert stream requires {@code alert:read} (§7.4, §2.4 —
     * the Alert Center is an OPERATOR surface).
     */
    public boolean mayAccess(Channel channel) {
        return channel != Channel.ALERTS || permissions.contains("alert:read");
    }

    public void subscribe(Channel channel) {
        subscriptions.add(channel);
    }

    public void unsubscribe(Channel channel) {
        subscriptions.remove(channel);
    }

    public boolean isSubscribedTo(Channel channel) {
        return subscriptions.contains(channel);
    }

    /** The next monotonic sequence number for a channel (first is 1). */
    public long nextSeq(Channel channel) {
        return seqByChannel.get(channel).incrementAndGet();
    }

    /** Serialises and sends a message; a send failure closes the connection rather than looping. */
    public void send(Object message) {
        if (!session.isOpen()) {
            return;
        }
        try {
            session.sendMessage(new TextMessage(objectMapper.writeValueAsString(message)));
        } catch (IOException | RuntimeException ex) {
            log.debug("Send to session {} failed; closing it", id(), ex);
            close(CloseStatus.SERVER_ERROR);
        }
    }

    public void close(CloseStatus status) {
        try {
            session.close(status);
        } catch (IOException ex) {
            log.debug("Closing session {} failed", id(), ex);
        }
    }
}
