package pk.nust.cuas.gateway.realtime;

import java.time.Clock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import pk.nust.cuas.gateway.realtime.message.ServerMessages;

/**
 * Drives the two periodic WebSocket duties (ARCHITECTURE.md §2.5.1, §2.5.2): flushing coalesced
 * track/sensor deltas, and sending the liveness heartbeat.
 *
 * <p>Kept apart from {@link StreamHub} and {@link SessionRegistry} so the timing is a scheduling
 * concern and the fan-out logic can be exercised without waiting on a scheduler. A scheduled method
 * that threw would be silently rescheduled by Spring, so both catch and log rather than dying quietly.
 */
@Component
public class RealtimeScheduler {

    private static final Logger log = LoggerFactory.getLogger(RealtimeScheduler.class);

    private final StreamHub hub;
    private final SessionRegistry registry;
    private final Clock clock;

    public RealtimeScheduler(StreamHub hub, SessionRegistry registry, Clock clock) {
        this.hub = hub;
        this.registry = registry;
        this.clock = clock;
    }

    @Scheduled(fixedRateString = "${gateway.ws.flush-interval-ms:250}")
    public void flush() {
        try {
            hub.flush();
        } catch (RuntimeException ex) {
            log.warn("WebSocket delta flush failed; it will run again at the next interval", ex);
        }
    }

    @Scheduled(fixedRateString = "${gateway.ws.heartbeat-interval-ms:5000}")
    public void heartbeat() {
        ServerMessages.Heartbeat beat = ServerMessages.Heartbeat.of(clock.instant().toString());
        for (GatewaySession session : registry.all()) {
            session.send(beat);
        }
    }
}
