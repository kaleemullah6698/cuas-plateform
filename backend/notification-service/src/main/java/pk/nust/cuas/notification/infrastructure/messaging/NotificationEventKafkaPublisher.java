package pk.nust.cuas.notification.infrastructure.messaging;

import com.google.protobuf.Timestamp;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import pk.nust.cuas.notification.application.port.NotificationEventPublisher;
import pk.nust.cuas.notification.domain.model.Channel;
import pk.nust.cuas.notification.domain.model.DeliveryFailureReason;
import pk.nust.cuas.notification.domain.model.Notification;
import pk.nust.cuas.notification.domain.model.NotificationStatus;
import pk.nust.cuas.notification.domain.model.SubjectRef;
import pk.nust.cuas.notification.v1.NotificationChannel;
import pk.nust.cuas.notification.v1.NotificationFailed;
import pk.nust.cuas.notification.v1.NotificationSent;

/**
 * Kafka adapter publishing delivery outcomes to {@code notification.sent.v1} and
 * {@code notification.failed.v1} (ARCHITECTURE.md sections 4.1.9, 5.2, 5.3.5).
 *
 * <p><b>Publication is best-effort and never blocks a delivery.</b> Unlike alert-service, whose event
 * <em>is</em> how the operator finds out and so must be transactional, here the delivery and its
 * receipt are already committed as the durable truth before this runs. A lost outcome event is a
 * reporting gap, not a missed notification, so a send failure is logged and dropped rather than
 * allowed to undo a delivery or trigger a redelivery. Events are keyed by notification id so an
 * aggregate's outcomes stay ordered on their partition.
 */
@Component
public class NotificationEventKafkaPublisher implements NotificationEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(NotificationEventKafkaPublisher.class);
    private static final String SCHEMA_VERSION = "1.0.0";

    private final KafkaTemplate<String, byte[]> kafka;

    public NotificationEventKafkaPublisher(KafkaTemplate<String, byte[]> kafka) {
        this.kafka = kafka;
    }

    @Override
    public void publishSent(Notification notification, long latencyMs) {
        NotificationSent.Builder builder = NotificationSent.newBuilder()
                .setNotificationId(notification.notificationId().toString())
                .setChannel(channel(notification.channel()))
                .setTrigger(trigger(notification.trigger()))
                .setDedupKey(notification.dedupKey())
                .setRecipientUserId(notification.recipientUserId().toString())
                .setAttempt(notification.attempt())
                .setLatencyMs((int) latencyMs)
                .setSchemaVersion(SCHEMA_VERSION)
                .setCorrelationId(notification.correlationId());
        applyRefs(notification.refs(), builder::setAlertId, builder::setIncidentId, builder::setTrackId);
        notification.providerMessageId().ifPresent(builder::setProviderMessageId);
        notification.sentAt().ifPresent(at -> builder.setSentAt(toTimestamp(at)));
        send(NotificationTopics.NOTIFICATION_SENT, notification.notificationId().toString(),
                builder.build().toByteArray());
    }

    @Override
    public void publishFailed(Notification notification, String detail) {
        boolean willRetry = notification.status() == NotificationStatus.FAILED_RETRYABLE;
        NotificationFailed.Builder builder = NotificationFailed.newBuilder()
                .setNotificationId(notification.notificationId().toString())
                .setChannel(channel(notification.channel()))
                .setTrigger(trigger(notification.trigger()))
                .setDedupKey(notification.dedupKey())
                .setRecipientUserId(notification.recipientUserId().toString())
                .setDetail(detail)
                .setAttempt(notification.attempt())
                .setWillRetry(willRetry)
                .setSchemaVersion(SCHEMA_VERSION)
                .setCorrelationId(notification.correlationId());
        applyRefs(notification.refs(), builder::setAlertId, builder::setIncidentId, builder::setTrackId);
        notification.reason().ifPresent(r -> builder.setReason(reason(r)));
        notification.failedAt().ifPresent(at -> builder.setFailedAt(toTimestamp(at)));
        if (willRetry) {
            notification.nextAttemptAt().ifPresent(at -> builder.setNextAttemptAt(toTimestamp(at)));
        }
        send(NotificationTopics.NOTIFICATION_FAILED, notification.notificationId().toString(),
                builder.build().toByteArray());
    }

    private void send(String topic, String key, byte[] payload) {
        kafka.send(topic, key, payload).whenComplete((result, ex) -> {
            if (ex != null) {
                log.warn("Could not publish to {} for notification {}", topic, key, ex);
            }
        });
    }

    private static void applyRefs(SubjectRef refs, java.util.function.Consumer<String> alert,
                                  java.util.function.Consumer<String> incident,
                                  java.util.function.Consumer<String> track) {
        refs.alertId().ifPresent(id -> alert.accept(id.toString()));
        refs.incidentId().ifPresent(id -> incident.accept(id.toString()));
        refs.trackId().ifPresent(id -> track.accept(id.toString()));
    }

    private static NotificationChannel channel(Channel channel) {
        return switch (channel) {
            case WEBSOCKET -> NotificationChannel.NOTIFICATION_CHANNEL_WEBSOCKET;
            case EMAIL -> NotificationChannel.NOTIFICATION_CHANNEL_EMAIL;
            case SMS -> NotificationChannel.NOTIFICATION_CHANNEL_SMS;
            case WEBHOOK -> NotificationChannel.NOTIFICATION_CHANNEL_WEBHOOK;
        };
    }

    private static pk.nust.cuas.notification.v1.NotificationTrigger trigger(
            pk.nust.cuas.notification.domain.model.NotificationTrigger trigger) {
        return switch (trigger) {
            case ALERT_RAISED -> pk.nust.cuas.notification.v1.NotificationTrigger.NOTIFICATION_TRIGGER_ALERT_RAISED;
            case ALERT_ESCALATED -> pk.nust.cuas.notification.v1.NotificationTrigger.NOTIFICATION_TRIGGER_ALERT_ESCALATED;
            case INCIDENT_CREATED -> pk.nust.cuas.notification.v1.NotificationTrigger.NOTIFICATION_TRIGGER_INCIDENT_CREATED;
            case SENSOR_OFFLINE -> pk.nust.cuas.notification.v1.NotificationTrigger.NOTIFICATION_TRIGGER_SENSOR_OFFLINE;
            case TEST -> pk.nust.cuas.notification.v1.NotificationTrigger.NOTIFICATION_TRIGGER_TEST;
        };
    }

    private static pk.nust.cuas.notification.v1.DeliveryFailureReason reason(DeliveryFailureReason reason) {
        return switch (reason) {
            case PROVIDER_ERROR -> pk.nust.cuas.notification.v1.DeliveryFailureReason.DELIVERY_FAILURE_REASON_PROVIDER_ERROR;
            case PROVIDER_TIMEOUT -> pk.nust.cuas.notification.v1.DeliveryFailureReason.DELIVERY_FAILURE_REASON_PROVIDER_TIMEOUT;
            case RATE_LIMITED -> pk.nust.cuas.notification.v1.DeliveryFailureReason.DELIVERY_FAILURE_REASON_RATE_LIMITED;
            case RECIPIENT_UNKNOWN -> pk.nust.cuas.notification.v1.DeliveryFailureReason.DELIVERY_FAILURE_REASON_RECIPIENT_UNKNOWN;
            case CHANNEL_DISABLED -> pk.nust.cuas.notification.v1.DeliveryFailureReason.DELIVERY_FAILURE_REASON_CHANNEL_DISABLED;
            case REJECTED -> pk.nust.cuas.notification.v1.DeliveryFailureReason.DELIVERY_FAILURE_REASON_REJECTED;
        };
    }

    private static Timestamp toTimestamp(Instant instant) {
        return Timestamp.newBuilder()
                .setSeconds(instant.getEpochSecond())
                .setNanos(instant.getNano())
                .build();
    }
}
