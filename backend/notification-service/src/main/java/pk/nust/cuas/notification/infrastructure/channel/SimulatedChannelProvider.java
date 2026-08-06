package pk.nust.cuas.notification.infrastructure.channel;

import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pk.nust.cuas.notification.application.port.ChannelProvider;
import pk.nust.cuas.notification.application.port.DeliveryAttempt;
import pk.nust.cuas.notification.domain.model.Channel;
import pk.nust.cuas.notification.domain.model.DeliveryResult;

/**
 * A channel provider that records a delivery without an external system — the dev-stack stand-in for
 * the channels that need infrastructure the platform does not provision here (an SMTP relay, an SMS
 * gateway; websocket push arrives with the M9 gateway fan-out).
 *
 * <p>This is the same mock-first stance the AI boundary takes (section 6.4): the pipeline runs end to
 * end and is testable, and a real provider drops in per channel behind the same {@link ChannelProvider}
 * SPI. It reports a deterministic success with a synthetic provider message id; it never fails, so
 * the retry path is exercised through the webhook provider against a failing endpoint instead.
 */
public final class SimulatedChannelProvider implements ChannelProvider {

    private static final Logger log = LoggerFactory.getLogger(SimulatedChannelProvider.class);

    private final Channel channel;

    public SimulatedChannelProvider(Channel channel) {
        this.channel = channel;
    }

    @Override
    public Channel channel() {
        return channel;
    }

    @Override
    public DeliveryResult deliver(DeliveryAttempt attempt) {
        log.info("[{} → {}] {}", channel, attempt.address(), attempt.subject());
        return new DeliveryResult.Sent(Optional.of("sim-" + attempt.notificationId()), 0L);
    }
}
