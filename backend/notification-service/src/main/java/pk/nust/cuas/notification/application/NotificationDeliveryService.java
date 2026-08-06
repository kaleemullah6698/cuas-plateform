package pk.nust.cuas.notification.application;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import pk.nust.cuas.notification.application.port.DeliveryAttempt;
import pk.nust.cuas.notification.domain.model.DeliveryResult;
import pk.nust.cuas.notification.domain.model.Notification;
import pk.nust.cuas.notification.domain.port.NotificationRepository;
import pk.nust.cuas.notification.application.port.NotificationEventPublisher;
import pk.nust.cuas.notification.domain.service.RetryPolicy;

/**
 * Delivers one notification and records the outcome — the step shared by first dispatch and by retry
 * (ARCHITECTURE.md section 4.1.9).
 *
 * <p>The provider call happens outside the transaction; only the outcome is persisted transactionally
 * (the notification's advanced status and its receipt), so a network delivery never holds a database
 * transaction open. The retry decision is the {@link RetryPolicy}'s: a retryable failure becomes
 * {@code FAILED_RETRYABLE} with a backed-off next attempt, a terminal one becomes {@code FAILED}. The
 * delivery event is published best-effort after the record commits — a lost {@code notification.sent}
 * event is a reporting gap, while the delivery and its receipt are the durable truth.
 */
@Service
public class NotificationDeliveryService {

    private static final Logger log = LoggerFactory.getLogger(NotificationDeliveryService.class);

    private final ChannelProviderRegistry providers;
    private final NotificationRepository notifications;
    private final NotificationEventPublisher publisher;
    private final RetryPolicy retryPolicy;
    private final Clock clock;

    public NotificationDeliveryService(ChannelProviderRegistry providers,
                                       NotificationRepository notifications,
                                       NotificationEventPublisher publisher,
                                       RetryPolicy retryPolicy, Clock clock) {
        this.providers = providers;
        this.notifications = notifications;
        this.publisher = publisher;
        this.retryPolicy = retryPolicy;
        this.clock = clock;
    }

    /**
     * Attempts delivery of a pending or retrying notification to an address.
     *
     * @param notification the notification to deliver
     * @param address      the recipient's address on the notification's channel
     * @return the notification after the attempt
     */
    public Notification deliver(Notification notification, String address) {
        long startMs = clock.millis();
        DeliveryResult result = providers.forChannel(notification.channel()).deliver(new DeliveryAttempt(
                notification.notificationId(), notification.channel(), address, notification.trigger(),
                notification.urgency(), notification.subject(), notification.refs(),
                notification.correlationId()));
        Instant at = clock.instant();

        return switch (result) {
            case DeliveryResult.Sent sent -> recordSent(notification, sent, at);
            case DeliveryResult.Failed failed -> recordFailed(notification, failed, at, startMs);
        };
    }

    /**
     * Records a notification as terminally undeliverable without attempting a provider — used when a
     * retry finds the recipient's channel no longer configured.
     *
     * @param notification the notification
     * @param reason       the terminal reason
     * @param detail       operator-facing detail
     * @return the failed notification
     */
    public Notification failTerminally(Notification notification,
                                       pk.nust.cuas.notification.domain.model.DeliveryFailureReason reason,
                                       String detail) {
        Instant at = clock.instant();
        Notification failed = notification.failedTerminal(reason, at);
        DeliveryResult.Failed result = new DeliveryResult.Failed(reason, detail);
        notifications.recordDelivery(failed, result, at);
        publishQuietly(() -> publisher.publishFailed(failed, detail));
        return failed;
    }

    private Notification recordSent(Notification notification, DeliveryResult.Sent sent, Instant at) {
        Notification delivered = notification.sent(sent.providerMessageId(), at);
        notifications.recordDelivery(delivered, sent, at);
        publishQuietly(() -> publisher.publishSent(delivered, sent.latencyMs()));
        log.debug("Delivered notification {} to {} on {}", delivered.notificationId(),
                delivered.recipientUserId(), delivered.channel());
        return delivered;
    }

    private Notification recordFailed(Notification notification, DeliveryResult.Failed failed, Instant at,
                                      long startMs) {
        int attemptsSoFar = notification.attempt() + 1;
        Optional<Duration> backoff = retryPolicy.nextBackoff(failed.reason(), attemptsSoFar);
        Notification outcome = backoff
                .map(delay -> notification.failedRetryable(failed.reason(), at, at.plus(delay)))
                .orElseGet(() -> notification.failedTerminal(failed.reason(), at));
        notifications.recordDelivery(outcome, failed, at);
        publishQuietly(() -> publisher.publishFailed(outcome, failed.detail()));
        log.debug("Delivery of notification {} failed ({}, {}); {}", outcome.notificationId(),
                failed.reason(), clock.millis() - startMs + " ms",
                backoff.isPresent() ? "will retry" : "terminal");
        return outcome;
    }

    private void publishQuietly(Runnable publish) {
        try {
            publish.run();
        } catch (RuntimeException ex) {
            // The delivery and its receipt are already committed; a lost reporting event must not undo
            // a successful delivery or trigger a redelivery.
            log.warn("Could not publish notification outcome event", ex);
        }
    }
}
