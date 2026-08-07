package pk.nust.cuas.gateway.realtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Clock;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketSession;
import pk.nust.cuas.gateway.config.GatewayWebSocketProperties;

/** The in-memory session registry: the per-user connection limit, subscription lookup, and removal. */
class SessionRegistryTest {

    private final SessionRegistry registry = new SessionRegistry(new ObjectMapper(), Clock.systemUTC(),
            new GatewayWebSocketProperties(250, 5000, 2, List.of()), new SimpleMeterRegistry());

    @Test
    void aSixthConnectionForAUserClosesTheOldest() throws java.io.IOException {
        WebSocketSession first = openSession("s1");
        registry.register(first, "usr-1", Set.of());
        registry.register(openSession("s2"), "usr-1", Set.of());

        registry.register(openSession("s3"), "usr-1", Set.of());   // limit is 2

        verify(first).close(any(CloseStatus.class));
        assertThat(registry.size()).isEqualTo(2);
        assertThat(registry.get("s1")).isNull();
    }

    @Test
    void limitsAreCountedPerUserNotGlobally() {
        registry.register(openSession("a1"), "user-a", Set.of());
        registry.register(openSession("a2"), "user-a", Set.of());
        registry.register(openSession("b1"), "user-b", Set.of());

        assertThat(registry.size()).isEqualTo(3);   // user-b's connection is not evicted
    }

    @Test
    void subscribersAreListedByChannelAndRemovedOnClose() {
        GatewaySession session = registry.register(openSession("s1"), "usr-1", Set.of());
        session.subscribe(Channel.TRACKS);

        assertThat(registry.subscribersOf(Channel.TRACKS)).containsExactly(session);
        assertThat(registry.subscribersOf(Channel.ALERTS)).isEmpty();

        registry.remove("s1", "usr-1");
        assertThat(registry.size()).isZero();
    }

    private static WebSocketSession openSession(String id) {
        WebSocketSession session = mock(WebSocketSession.class);
        when(session.getId()).thenReturn(id);
        when(session.isOpen()).thenReturn(true);
        return session;
    }
}
