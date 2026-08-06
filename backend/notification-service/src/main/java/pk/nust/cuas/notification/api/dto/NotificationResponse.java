package pk.nust.cuas.notification.api.dto;

import java.time.Instant;
import pk.nust.cuas.notification.domain.model.Channel;
import pk.nust.cuas.notification.domain.model.NotificationStatus;
import pk.nust.cuas.notification.domain.model.NotificationTrigger;
import pk.nust.cuas.notification.domain.model.Urgency;
import pk.nust.cuas.notification.domain.model.Notification;

/**
 * A notification as returned by the history and test endpoints (section 4.1.9).
 *
 * <p>Carries the references an operator needs to open what it concerns, never a channel address:
 * the address is personal data that lives in {@code notifydb} keyed by recipient and channel, and
 * has no place in a response the console renders. The one-line subject is the only content.
 *
 * @param notificationId    the notification
 * @param channel           the channel it was delivered on
 * @param trigger           what it was about
 * @param urgency           how urgent
 * @param status            its current delivery status
 * @param subject           one-line operator-facing text
 * @param attempt           how many delivery attempts have been made
 * @param alertId           the alert it concerns, if any
 * @param incidentId        the incident it concerns, if any
 * @param trackId           the track it concerns, if any
 * @param reason            why the last attempt failed, if it did
 * @param providerMessageId the provider's handle for the message, if it returned one
 * @param createdAt         when it was created
 * @param sentAt            when it was delivered, if it was
 * @param failedAt          when it last failed, if it did
 * @param nextAttemptAt     when the next retry is due, if it is retrying
 * @param correlationId     the end-to-end trace
 */
public record NotificationResponse(String notificationId, Channel channel, NotificationTrigger trigger,
                                   Urgency urgency, NotificationStatus status, String subject, int attempt,
                                   String alertId, String incidentId, String trackId, String reason,
                                   String providerMessageId, Instant createdAt, Instant sentAt,
                                   Instant failedAt, Instant nextAttemptAt, String correlationId) {

    public static NotificationResponse from(Notification n) {
        return new NotificationResponse(
                n.notificationId().toString(), n.channel(), n.trigger(), n.urgency(), n.status(),
                n.subject(), n.attempt(),
                n.refs().alertId().map(Object::toString).orElse(null),
                n.refs().incidentId().map(Object::toString).orElse(null),
                n.refs().trackId().map(Object::toString).orElse(null),
                n.reason().map(Enum::name).orElse(null),
                n.providerMessageId().orElse(null), n.createdAt(),
                n.sentAt().orElse(null), n.failedAt().orElse(null), n.nextAttemptAt().orElse(null),
                n.correlationId());
    }
}
