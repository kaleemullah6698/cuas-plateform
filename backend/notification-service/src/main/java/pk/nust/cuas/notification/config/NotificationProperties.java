package pk.nust.cuas.notification.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Tunable parameters of notification delivery (ARCHITECTURE.md section 4.1.9), bound from the
 * {@code notification.*} namespace with defaults that make the service runnable with no configuration.
 *
 * @param retry   the retry-with-backoff schedule
 * @param webhook the webhook channel's HTTP timeouts
 */
@ConfigurationProperties(prefix = "notification")
public record NotificationProperties(@DefaultValue Retry retry, @DefaultValue Webhook webhook) {

    /**
     * @param maxAttempts   total attempts allowed, including the first
     * @param baseBackoff   delay after the first failure
     * @param maxBackoff    the cap the exponential backoff never exceeds
     * @param sweepBatch    how many due notifications one sweep re-delivers
     * @param checkInterval how often the retry sweep runs
     * @param lease         how long a claimed retry is held before another sweep may re-claim it
     */
    public record Retry(
            @DefaultValue("5") int maxAttempts,
            @DefaultValue("PT10S") Duration baseBackoff,
            @DefaultValue("PT5M") Duration maxBackoff,
            @DefaultValue("100") int sweepBatch,
            @DefaultValue("PT15S") Duration checkInterval,
            @DefaultValue("PT2M") Duration lease) {
    }

    /**
     * @param connectTimeout how long to wait to connect to a webhook endpoint
     * @param readTimeout    how long to wait for its response
     */
    public record Webhook(
            @DefaultValue("PT3S") Duration connectTimeout,
            @DefaultValue("PT5S") Duration readTimeout) {
    }
}
