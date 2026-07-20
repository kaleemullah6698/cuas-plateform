package pk.nust.cuas.gateway.infrastructure.messaging.producer;

import com.google.protobuf.Timestamp;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import pk.nust.cuas.gateway.v1.GatewayAccess;
import pk.nust.cuas.gateway.v1.GatewayRateLimited;

/**
 * Publishes the two edge telemetry streams of ARCHITECTURE.md sections 4.1.1 and 5.2.
 *
 * <p>Both sends are fire-and-forget, which is the opposite of how the audit publisher behaves and
 * is deliberate. These are observability events: losing one costs a line on a dashboard. Blocking
 * an operator's request on a Kafka acknowledgement to record that the request happened would make
 * the broker an availability dependency of every call through the gateway, which section 4.1.1
 * explicitly avoids by keeping the gateway stateless and dependency-light.
 *
 * <p>The audit trail is a separate mechanism with the opposite guarantee: a rejection on a
 * security-relevant endpoint is additionally published through the fail-closed
 * {@code AuditPublisher} (section 7.8), and that one is not allowed to be lost.
 */
@Component
public class GatewayEventPublisher {

    /** Section 5.2 topic catalogue. */
    public static final String TOPIC_ACCESS = "platform.gateway.access.v1";

    /** Section 5.2 topic catalogue. */
    public static final String TOPIC_RATE_LIMIT = "platform.gateway.ratelimit.v1";

    private static final Logger log = LoggerFactory.getLogger(GatewayEventPublisher.class);

    private final KafkaTemplate<String, byte[]> kafkaTemplate;

    public GatewayEventPublisher(KafkaTemplate<String, byte[]> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    /**
     * @param event the access record; keyed by user id so one user's traffic stays ordered
     */
    public void publishAccess(GatewayAccess event) {
        String key = event.getUserId().isEmpty() ? event.getClientIp() : event.getUserId();
        send(TOPIC_ACCESS, key, event.toByteArray());
    }

    /**
     * @param event the rejection; keyed by the exhausted bucket's identity
     */
    public void publishRateLimited(GatewayRateLimited event) {
        send(TOPIC_RATE_LIMIT, event.getIdentity(), event.toByteArray());
    }

    private void send(String topic, String key, byte[] payload) {
        try {
            kafkaTemplate.send(topic, key, payload)
                    .whenComplete((result, failure) -> {
                        if (failure != null) {
                            log.warn("Could not publish to {}: {}", topic, failure.toString());
                        }
                    });
        } catch (RuntimeException ex) {
            // A telemetry failure must never surface to the caller as a failed request.
            log.warn("Could not enqueue a publish to {}: {}", topic, ex.toString());
        }
    }

    /**
     * @param instant the instant to convert
     * @return the protobuf timestamp equivalent
     */
    public static Timestamp toTimestamp(Instant instant) {
        return Timestamp.newBuilder()
                .setSeconds(instant.getEpochSecond())
                .setNanos(instant.getNano())
                .build();
    }
}
