package pk.nust.cuas.user.infrastructure.messaging;

import com.google.protobuf.Timestamp;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import pk.nust.cuas.commons.correlation.CorrelationId;
import pk.nust.cuas.user.v1.UserDeactivated;

/**
 * Announces user lifecycle changes other services must react to (ARCHITECTURE.md section 4.1.3).
 *
 * <p>Only deactivation is published today, because it is the only one with a consumer:
 * auth-service revokes the account's sessions and refresh-token families on receipt (section
 * 4.1.2). Publishing events nothing consumes would be contract surface with no meaning.
 *
 * <p>The send is synchronous for the same reason the audit publish is: a deactivation that was
 * not announced would leave the user able to act until their access token expired, so the caller
 * must learn about the failure and roll back rather than proceed in a false-secure state.
 */
@Component
public class UserEventPublisher {

    /** Section 5.2 topic catalogue. */
    public static final String TOPIC_USER_DEACTIVATED = "platform.user.deactivated.v1";

    private static final Logger log = LoggerFactory.getLogger(UserEventPublisher.class);
    private static final long PUBLISH_TIMEOUT_SECONDS = 5;

    private final KafkaTemplate<String, byte[]> kafkaTemplate;

    public UserEventPublisher(KafkaTemplate<String, byte[]> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    /**
     * @param userId        the deactivated account
     * @param deactivatedBy who deactivated it
     */
    public void publishDeactivated(UUID userId, String deactivatedBy) {
        Instant now = Instant.now();
        UserDeactivated event = UserDeactivated.newBuilder()
                .setUserId(userId.toString())
                .setDeactivatedBy(deactivatedBy)
                .setDeactivatedAt(Timestamp.newBuilder()
                        .setSeconds(now.getEpochSecond()).setNanos(now.getNano()).build())
                .setCorrelationId(CorrelationId.current().orElse(""))
                .build();

        try {
            kafkaTemplate.send(TOPIC_USER_DEACTIVATED, userId.toString(), event.toByteArray())
                    .get(PUBLISH_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            log.debug("Announced deactivation of user {}", userId);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted announcing deactivation of " + userId, ex);
        } catch (Exception ex) {
            throw new IllegalStateException(
                    "Could not announce deactivation of " + userId
                            + "; aborting so the account is not left able to act", ex);
        }
    }
}
