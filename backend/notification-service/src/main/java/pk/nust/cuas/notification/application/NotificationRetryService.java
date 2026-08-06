package pk.nust.cuas.notification.application;

import java.time.Clock;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import pk.nust.cuas.commons.correlation.CorrelationId;
import pk.nust.cuas.notification.config.NotificationProperties;
import pk.nust.cuas.notification.domain.model.ChannelPreference;
import pk.nust.cuas.notification.domain.model.DeliveryFailureReason;
import pk.nust.cuas.notification.domain.model.Notification;
import pk.nust.cuas.notification.domain.port.NotificationRepository;
import pk.nust.cuas.notification.domain.port.PreferencesRepository;

/**
 * Re-delivers notifications whose earlier attempt failed retryably and whose backoff has elapsed
 * (ARCHITECTURE.md section 4.1.9).
 *
 * <p>Claims a batch of due notifications — skipping any another instance is already retrying, so the
 * sweep is safe to run everywhere at once — and re-delivers each against the recipient's <em>current</em>
 * channel address. A channel disabled or removed since the first attempt makes the notification
 * terminally undeliverable rather than looping forever. The cadence lives in the infrastructure layer;
 * this holds only what a sweep does.
 */
@Service
public class NotificationRetryService {

    private static final Logger log = LoggerFactory.getLogger(NotificationRetryService.class);

    private final NotificationRepository notifications;
    private final PreferencesRepository preferences;
    private final NotificationDeliveryService deliveryService;
    private final Clock clock;
    private final int batchLimit;

    public NotificationRetryService(NotificationRepository notifications,
                                    PreferencesRepository preferences,
                                    NotificationDeliveryService deliveryService,
                                    NotificationProperties properties, Clock clock) {
        this.notifications = notifications;
        this.preferences = preferences;
        this.deliveryService = deliveryService;
        this.clock = clock;
        this.batchLimit = properties.retry().sweepBatch();
    }

    /** Re-delivers every notification whose retry is due. */
    public void sweep() {
        List<Notification> due = notifications.claimRetryable(clock.instant(), batchLimit);
        if (due.isEmpty()) {
            return;
        }
        log.debug("Retry sweep: {} due notification(s)", due.size());
        for (Notification notification : due) {
            CorrelationId.set(correlationId(notification));
            try {
                retry(notification);
            } catch (RuntimeException ex) {
                log.warn("Retry of notification {} failed", notification.notificationId(), ex);
            } finally {
                CorrelationId.clear();
            }
        }
    }

    private void retry(Notification notification) {
        Optional<String> address = preferences.find(notification.recipientUserId())
                .flatMap(prefs -> prefs.channels().stream()
                        .filter(channel -> channel.channel() == notification.channel() && channel.enabled())
                        .map(ChannelPreference::address)
                        .findFirst());
        if (address.isEmpty()) {
            deliveryService.failTerminally(notification, DeliveryFailureReason.CHANNEL_DISABLED,
                    "the recipient's channel is no longer configured");
            return;
        }
        deliveryService.deliver(notification, address.get());
    }

    private String correlationId(Notification notification) {
        return notification.correlationId().isBlank()
                ? CorrelationId.generate()
                : notification.correlationId();
    }
}
