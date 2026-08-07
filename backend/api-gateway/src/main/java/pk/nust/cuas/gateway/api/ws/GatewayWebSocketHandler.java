package pk.nust.cuas.gateway.api.ws;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import pk.nust.cuas.gateway.realtime.Channel;
import pk.nust.cuas.gateway.realtime.GatewaySession;
import pk.nust.cuas.gateway.realtime.SessionRegistry;
import pk.nust.cuas.gateway.realtime.StreamHub;
import pk.nust.cuas.gateway.realtime.message.ClientCommand;
import pk.nust.cuas.gateway.realtime.message.ServerMessages;

/**
 * The {@code /ws} endpoint (ARCHITECTURE.md §3.5). Registers the connection, sends {@code CONNECTED},
 * and handles the client's {@code SUBSCRIBE} / {@code RESNAPSHOT} commands: a subscribe authorises the
 * channel, records it, and sends the current {@code SNAPSHOT}; a resnapshot re-sends it with a fresh
 * {@code seq} (the client's answer to a detected gap).
 */
@Component
public class GatewayWebSocketHandler extends TextWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(GatewayWebSocketHandler.class);

    private final SessionRegistry registry;
    private final StreamHub hub;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public GatewayWebSocketHandler(SessionRegistry registry, StreamHub hub, ObjectMapper objectMapper,
                                   Clock clock) {
        this.registry = registry;
        this.hub = hub;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Override
    @SuppressWarnings("unchecked")
    public void afterConnectionEstablished(WebSocketSession session) {
        String userId = (String) session.getAttributes().get(JwtHandshakeInterceptor.USER_ID);
        Set<String> permissions =
                (Set<String>) session.getAttributes().getOrDefault(JwtHandshakeInterceptor.PERMISSIONS,
                        Set.of());
        GatewaySession managed = registry.register(session, userId, permissions);
        managed.send(ServerMessages.Connected.of(managed.id(), clock.instant().toString()));
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        GatewaySession managed = registry.get(session.getId());
        if (managed == null) {
            return;
        }
        ClientCommand command;
        try {
            command = objectMapper.readValue(message.getPayload(), ClientCommand.class);
        } catch (RuntimeException | com.fasterxml.jackson.core.JsonProcessingException ex) {
            managed.send(ServerMessages.Error.of("bad-message", "unparseable command", false));
            return;
        }
        switch (command.type() == null ? "" : command.type()) {
            case ClientCommand.SUBSCRIBE -> subscribe(managed, command.channels());
            case ClientCommand.RESNAPSHOT -> resnapshot(managed, command.channel());
            case ClientCommand.UNSUBSCRIBE -> Channel.fromWire(command.channel())
                    .ifPresent(managed::unsubscribe);
            default -> managed.send(ServerMessages.Error.of("unknown-command",
                    "unknown command type", false));
        }
    }

    private void subscribe(GatewaySession session, List<String> channelNames) {
        if (channelNames == null) {
            return;
        }
        for (String name : channelNames) {
            Optional<Channel> channel = Channel.fromWire(name);
            if (channel.isEmpty()) {
                session.send(ServerMessages.Error.of("unknown-channel", name, false));
                continue;
            }
            Channel resolved = channel.get();
            if (!session.mayAccess(resolved)) {
                session.send(ServerMessages.Error.of("forbidden",
                        "not permitted to subscribe to " + name, false));
                continue;
            }
            session.subscribe(resolved);
            sendSnapshot(session, resolved);
        }
    }

    private void resnapshot(GatewaySession session, String channelName) {
        Channel.fromWire(channelName)
                .filter(session::isSubscribedTo)
                .ifPresent(channel -> sendSnapshot(session, channel));
    }

    /** Sends the current snapshot for a channel that carries one (tracks, sensors). */
    private void sendSnapshot(GatewaySession session, Channel channel) {
        Object payload = hub.snapshotPayload(channel);
        if (payload != null) {
            session.send(ServerMessages.Snapshot.of(channel, session.nextSeq(channel), payload));
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        String userId = (String) session.getAttributes().get(JwtHandshakeInterceptor.USER_ID);
        registry.remove(session.getId(), userId);
        log.debug("WebSocket session {} closed ({})", session.getId(), status);
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        log.debug("WebSocket transport error on {}", session.getId(), exception);
    }
}
