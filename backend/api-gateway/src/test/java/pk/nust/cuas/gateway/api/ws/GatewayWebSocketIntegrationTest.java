package pk.nust.cuas.gateway.api.ws;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import pk.nust.cuas.gateway.domain.policy.RateLimitPolicy;
import pk.nust.cuas.gateway.realtime.StreamHub;
import pk.nust.cuas.gateway.realtime.view.AlertView;
import pk.nust.cuas.gateway.realtime.view.TrackView;

/**
 * The WebSocket protocol end to end against the running gateway (ARCHITECTURE.md §3.5): handshake auth,
 * subscribe → snapshot, coalesced delta, immediate alert, heartbeat, resnapshot, and per-channel
 * authorisation. The JWKS decoder and the rate-limit policy are stubbed so the test needs neither a
 * real key set nor Redis; the fan-out is driven by feeding the {@link StreamHub} directly, which is why
 * the Kafka listeners are turned off — what is under test is the protocol, not the broker.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.kafka.listener.auto-startup=false",
        "gateway.ws.heartbeat-interval-ms=400",
        "gateway.ws.flush-interval-ms=100",
        "gateway.access-log.sample-rate=0.0"
})
class GatewayWebSocketIntegrationTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    @LocalServerPort
    private int port;

    @Autowired
    private StreamHub hub;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @MockitoBean
    private RateLimitPolicy rateLimitPolicy;

    @BeforeEach
    void stubs() {
        when(jwtDecoder.decode("operator")).thenReturn(token("usr-1", List.of("alert:read")));
        when(jwtDecoder.decode("viewer")).thenReturn(token("usr-2", List.of("track:read")));
        when(jwtDecoder.decode("bad")).thenThrow(new BadJwtException("invalid"));
        when(rateLimitPolicy.bucketsFor(any())).thenReturn(List.of());
    }

    @Test
    void connectsSubscribesSnapshotsAndStreams() throws Exception {
        BlockingQueue<String> inbox = new LinkedBlockingQueue<>();
        WebSocketSession session = connect("operator", inbox);
        try {
            assertThat(awaitType(inbox, "CONNECTED")).isNotNull();

            session.sendMessage(new TextMessage(
                    "{\"type\":\"SUBSCRIBE\",\"channels\":[\"tracks\",\"alerts\"]}"));
            JsonNode snapshot = awaitType(inbox, "SNAPSHOT");
            assertThat(snapshot.get("channel").asText()).isEqualTo("tracks");
            assertThat(snapshot.get("seq").asLong()).isEqualTo(1L);

            // A track update coalesces into a DELTA on the flush window.
            hub.onTrack(new TrackView("t1", "sector-NE", "CONFIRMED", 33.6, 73.0, 120.0, 14.0, 270.0,
                    "drone", 0.9, null, null, 3));
            JsonNode delta = awaitType(inbox, "DELTA");
            assertThat(delta.get("payload").get("changed").get(0).get("trackId").asText())
                    .isEqualTo("t1");

            // An alert is pushed immediately, never coalesced.
            hub.onAlert(new AlertView("a1", "ALT-1", "CRITICAL", "t1", "title", "summary", null));
            JsonNode alert = awaitType(inbox, "ALERT");
            assertThat(alert.get("alert").get("reference").asText()).isEqualTo("ALT-1");

            // A resnapshot re-sends the tracks snapshot with a fresh seq.
            session.sendMessage(new TextMessage("{\"type\":\"RESNAPSHOT\",\"channel\":\"tracks\"}"));
            assertThat(awaitType(inbox, "SNAPSHOT").get("seq").asLong()).isGreaterThan(1L);

            // The liveness heartbeat arrives on its cadence.
            assertThat(awaitType(inbox, "HEARTBEAT")).isNotNull();
        } finally {
            session.close();
        }
    }

    @Test
    void rejectsAnInvalidToken() {
        assertThatThrownBy(() -> connect("bad", new LinkedBlockingQueue<>()))
                .isInstanceOf(ExecutionException.class);
    }

    @Test
    void refusesTheAlertsChannelWithoutAlertReadPermission() throws Exception {
        BlockingQueue<String> inbox = new LinkedBlockingQueue<>();
        WebSocketSession session = connect("viewer", inbox);
        try {
            awaitType(inbox, "CONNECTED");
            session.sendMessage(new TextMessage("{\"type\":\"SUBSCRIBE\",\"channels\":[\"alerts\"]}"));

            JsonNode error = awaitType(inbox, "ERROR");
            assertThat(error.get("code").asText()).isEqualTo("forbidden");
        } finally {
            session.close();
        }
    }

    private WebSocketSession connect(String token, BlockingQueue<String> inbox) throws Exception {
        return new StandardWebSocketClient().execute(new TextWebSocketHandler() {
            @Override
            protected void handleTextMessage(WebSocketSession s, TextMessage message) {
                inbox.add(message.getPayload());
            }
        }, "ws://localhost:" + port + "/ws?token=" + token).get(5, TimeUnit.SECONDS);
    }

    /** Polls the inbox for the next message of a type, draining others (heartbeats may interleave). */
    private static JsonNode awaitType(BlockingQueue<String> inbox, String type) throws Exception {
        long deadline = System.currentTimeMillis() + 5000;
        while (System.currentTimeMillis() < deadline) {
            String message = inbox.poll(deadline - System.currentTimeMillis(), TimeUnit.MILLISECONDS);
            if (message == null) {
                break;
            }
            JsonNode node = JSON.readTree(message);
            if (type.equals(node.get("type").asText())) {
                return node;
            }
        }
        throw new AssertionError("no " + type + " message arrived within the timeout");
    }

    private static Jwt token(String subject, List<String> perms) {
        return Jwt.withTokenValue("t").header("alg", "none").subject(subject)
                .claim("perms", perms).issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(600))
                .build();
    }
}
