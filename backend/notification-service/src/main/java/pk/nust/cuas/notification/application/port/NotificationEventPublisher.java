package pk.nust.cuas.notification.application.port;

import pk.nust.cuas.notification.domain.model.Notification;

/**
 * Publishes delivery outcomes to Kafka (ARCHITECTURE.md sections 4.1.9, 5.2, 5.3.5).
 *
 * <p>An output port owned by the application layer and implemented by the Kafka adapter, which maps
 * the domain {@link Notification} to the {@code NotificationSent} / {@code NotificationFailed}
 * contract. Both events describe an attempt's outcome, never the message content. Failures are
 * published per attempt, not only on final failure, so a channel degrading through retries is visible
 * before it gives up.
 */
public interface NotificationEventPublisher {

    /**
     * Publishes a successful delivery to {@code notification.sent.v1}.
     *
     * @param notification the sent notification
     * @param latencyMs    how long the successful attempt took
     */
    void publishSent(Notification notification, long latencyMs);

    /**
     * Publishes a failed attempt to {@code notification.failed.v1}.
     *
     * @param notification the notification whose attempt failed (its status says whether it will retry)
     * @param detail       provider-reported detail; never the message body
     */
    void publishFailed(Notification notification, String detail);
}
