package pk.nust.cuas.auth.infrastructure.messaging;

import com.google.protobuf.InvalidProtocolBufferException;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import pk.nust.cuas.auth.application.AuthenticationService;
import pk.nust.cuas.user.v1.UserDeactivated;

/**
 * Revokes a deactivated user's access (ARCHITECTURE.md section 4.1.2).
 *
 * <p>Deactivating an account in user-service does not, by itself, stop the holder acting: their
 * access token stays valid until it expires. This consumer closes that window by revoking every
 * session and refresh-token family as soon as the deactivation is announced.
 *
 * <p>Revocation is idempotent — it only affects rows that are not already revoked — so
 * at-least-once redelivery is harmless.
 */
@Component
public class UserDeactivatedConsumer {

    /** Section 5.2 topic catalogue. */
    public static final String TOPIC = "platform.user.deactivated.v1";

    private static final Logger log = LoggerFactory.getLogger(UserDeactivatedConsumer.class);

    private final AuthenticationService authentication;

    public UserDeactivatedConsumer(AuthenticationService authentication) {
        this.authentication = authentication;
    }

    @KafkaListener(topics = TOPIC,
            groupId = "${spring.kafka.consumer.group-id:auth-service}",
            concurrency = "1")
    @Transactional
    public void onUserDeactivated(byte[] payload) {
        UserDeactivated event;
        try {
            event = UserDeactivated.parseFrom(payload);
        } catch (InvalidProtocolBufferException ex) {
            // Cannot become valid on retry; redelivering would stall the partition.
            log.error("Discarding malformed deactivation event ({} bytes)", payload.length, ex);
            return;
        }

        UUID userId = UUID.fromString(event.getUserId());
        log.info("User {} deactivated by {}; revoking sessions and refresh tokens",
                userId, event.getDeactivatedBy());
        authentication.revokeAllForUser(userId, "USER_DEACTIVATED");
    }
}
