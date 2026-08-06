package pk.nust.cuas.notification.domain.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** The immutable transitions of the {@link Notification} aggregate. */
class NotificationTest {

    private final Instant now = Instant.parse("2026-08-06T12:00:00Z");

    @Test
    void aNewNotificationIsPendingWithNoAttempts() {
        Notification n = open();

        assertThat(n.status()).isEqualTo(NotificationStatus.PENDING);
        assertThat(n.attempt()).isZero();
        assertThat(n.sentAt()).isEmpty();
    }

    @Test
    void sendingAdvancesTheAttemptAndRecordsTheProviderHandle() {
        Notification sent = open().sent(Optional.of("provider-42"), now);

        assertThat(sent.status()).isEqualTo(NotificationStatus.SENT);
        assertThat(sent.attempt()).isEqualTo(1);
        assertThat(sent.sentAt()).contains(now);
        assertThat(sent.providerMessageId()).contains("provider-42");
    }

    @Test
    void aRetryableFailureCarriesAReasonAndANextAttempt() {
        Instant next = now.plusSeconds(10);
        Notification failed = open().failedRetryable(DeliveryFailureReason.PROVIDER_TIMEOUT, now, next);

        assertThat(failed.status()).isEqualTo(NotificationStatus.FAILED_RETRYABLE);
        assertThat(failed.attempt()).isEqualTo(1);
        assertThat(failed.reason()).contains(DeliveryFailureReason.PROVIDER_TIMEOUT);
        assertThat(failed.nextAttemptAt()).contains(next);
    }

    @Test
    void aTerminalFailureHasNoNextAttempt() {
        Notification failed = open().failedTerminal(DeliveryFailureReason.RECIPIENT_UNKNOWN, now);

        assertThat(failed.status()).isEqualTo(NotificationStatus.FAILED);
        assertThat(failed.nextAttemptAt()).isEmpty();
        assertThat(failed.reason()).contains(DeliveryFailureReason.RECIPIENT_UNKNOWN);
    }

    @Test
    void suppressionIsNotAnAttempt() {
        Notification suppressed = open().suppressed();

        assertThat(suppressed.status()).isEqualTo(NotificationStatus.SUPPRESSED);
        assertThat(suppressed.attempt()).isZero();
    }

    private Notification open() {
        return Notification.open(UUID.randomUUID(), UUID.randomUUID(), Channel.EMAIL,
                NotificationTrigger.ALERT_RAISED, "dedup-1", Urgency.HIGH, SubjectRef.none(),
                "subject", "corr-1", now);
    }
}
