package pk.nust.cuas.notification.domain.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import pk.nust.cuas.notification.domain.model.DeliveryFailureReason;

/** The backoff and give-up rules of {@link RetryPolicy}. */
class RetryPolicyTest {

    private final RetryPolicy policy =
            new RetryPolicy(5, Duration.ofSeconds(10), Duration.ofMinutes(5));

    @Test
    void aTerminalFailureIsNeverRetried() {
        assertThat(policy.nextBackoff(DeliveryFailureReason.RECIPIENT_UNKNOWN, 1)).isEmpty();
        assertThat(policy.nextBackoff(DeliveryFailureReason.CHANNEL_DISABLED, 1)).isEmpty();
        assertThat(policy.nextBackoff(DeliveryFailureReason.REJECTED, 1)).isEmpty();
    }

    @Test
    void aRetryableFailureBacksOffExponentiallyFromTheBase() {
        assertThat(policy.nextBackoff(DeliveryFailureReason.PROVIDER_TIMEOUT, 1))
                .contains(Duration.ofSeconds(10));
        assertThat(policy.nextBackoff(DeliveryFailureReason.PROVIDER_TIMEOUT, 2))
                .contains(Duration.ofSeconds(20));
        assertThat(policy.nextBackoff(DeliveryFailureReason.PROVIDER_TIMEOUT, 3))
                .contains(Duration.ofSeconds(40));
    }

    @Test
    void theBackoffNeverExceedsTheCap() {
        // 10s << 20 would be far past five minutes; it is clamped to the cap instead.
        assertThat(policy.nextBackoff(DeliveryFailureReason.PROVIDER_ERROR, 4))
                .contains(Duration.ofSeconds(80));
        RetryPolicy manyAttempts = new RetryPolicy(20, Duration.ofSeconds(10), Duration.ofMinutes(5));
        assertThat(manyAttempts.nextBackoff(DeliveryFailureReason.PROVIDER_ERROR, 10))
                .contains(Duration.ofMinutes(5));
    }

    @Test
    void retryingStopsAtTheAttemptCeiling() {
        assertThat(policy.nextBackoff(DeliveryFailureReason.PROVIDER_TIMEOUT, 5)).isEmpty();
        assertThat(policy.nextBackoff(DeliveryFailureReason.PROVIDER_TIMEOUT, 6)).isEmpty();
    }
}
