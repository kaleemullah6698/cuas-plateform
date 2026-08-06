package pk.nust.cuas.notification.application.port;

import java.util.UUID;
import pk.nust.cuas.notification.domain.model.Channel;
import pk.nust.cuas.notification.domain.model.NotificationTrigger;
import pk.nust.cuas.notification.domain.model.SubjectRef;
import pk.nust.cuas.notification.domain.model.Urgency;

/**
 * Everything a {@link ChannelProvider} needs to make one delivery: the address to reach, the message
 * to convey, and the context a webhook payload or an operator diagnosing a channel would want. Never
 * carries a message body beyond the one-line subject — the full content lives on the source topic.
 *
 * @param notificationId the notification being delivered
 * @param channel        the channel
 * @param address        the recipient's address on that channel
 * @param trigger        what it is about
 * @param urgency        how urgent
 * @param subject        one-line operator-facing text
 * @param refs           the alert/incident/track it concerns
 * @param correlationId  the end-to-end trace
 */
public record DeliveryAttempt(UUID notificationId, Channel channel, String address,
                              NotificationTrigger trigger, Urgency urgency, String subject,
                              SubjectRef refs, String correlationId) {
}
