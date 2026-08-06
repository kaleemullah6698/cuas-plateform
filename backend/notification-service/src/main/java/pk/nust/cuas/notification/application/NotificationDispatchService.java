package pk.nust.cuas.notification.application;

import java.time.Clock;
import java.time.LocalTime;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import pk.nust.cuas.commons.id.Uuidv7Generator;
import pk.nust.cuas.notification.domain.model.ChannelPreference;
import pk.nust.cuas.notification.domain.model.Notification;
import pk.nust.cuas.notification.domain.model.NotificationTrigger;
import pk.nust.cuas.notification.domain.model.RecipientPreferences;
import pk.nust.cuas.notification.domain.model.SubjectRef;
import pk.nust.cuas.notification.domain.model.TriggerEvent;
import pk.nust.cuas.notification.domain.model.Urgency;
import pk.nust.cuas.notification.domain.port.NotificationRepository;
import pk.nust.cuas.notification.domain.port.PreferencesRepository;
import pk.nust.cuas.notification.domain.service.DeliverabilityPolicy;

/**
 * Fans a source event out to the recipients who should be notified (ARCHITECTURE.md section 4.1.9).
 *
 * <p>For each recipient with a configured channel: the deliverability policy decides whether the
 * event is worth their attention now; the preferred channel is chosen (one, not all — cross-channel
 * deduplication); a pending notification is created, which the {@code (recipient, dedupKey)}
 * uniqueness drops if the recipient was already notified about this thing; and it is then delivered,
 * or recorded suppressed if quiet hours held it. One recipient failing never stops the others.
 */
@Service
public class NotificationDispatchService {

    private static final Logger log = LoggerFactory.getLogger(NotificationDispatchService.class);

    private final PreferencesRepository preferences;
    private final DeliverabilityPolicy deliverability;
    private final NotificationRepository notifications;
    private final NotificationDeliveryService deliveryService;
    private final Uuidv7Generator idGenerator;
    private final Clock clock;

    public NotificationDispatchService(PreferencesRepository preferences,
                                       DeliverabilityPolicy deliverability,
                                       NotificationRepository notifications,
                                       NotificationDeliveryService deliveryService,
                                       Uuidv7Generator idGenerator, Clock clock) {
        this.preferences = preferences;
        this.deliverability = deliverability;
        this.notifications = notifications;
        this.deliveryService = deliveryService;
        this.idGenerator = idGenerator;
        this.clock = clock;
    }

    /**
     * Dispatches a source event to every eligible recipient.
     *
     * @param event the event to notify about
     */
    public void dispatch(TriggerEvent event) {
        LocalTime now = LocalTime.now(clock);
        for (RecipientPreferences recipient : preferences.recipientsWithEnabledChannel()) {
            try {
                dispatchToRecipient(event, recipient, now);
            } catch (RuntimeException ex) {
                log.warn("Notifying recipient {} of {} failed", recipient.userId(), event.trigger(), ex);
            }
        }
    }

    /**
     * Sends a test notification to one user on their preferred channel, bypassing quiet hours and the
     * severity floor — a test is user-initiated verification that their channel works
     * ({@code POST /notifications/test}).
     *
     * @param userId        the caller
     * @param correlationId the request's correlation id
     * @return the delivered notification, or empty if the user has no enabled channel
     */
    public Optional<Notification> sendTest(UUID userId, String correlationId) {
        Optional<RecipientPreferences> preferencesForUser = preferences.find(userId);
        if (preferencesForUser.isEmpty()) {
            return Optional.empty();
        }
        Optional<ChannelPreference> channel = preferencesForUser.get().preferredChannel();
        if (channel.isEmpty()) {
            return Optional.empty();
        }

        UUID id = idGenerator.generate(clock.instant());
        Notification notification = Notification.open(id, userId, channel.get().channel(),
                NotificationTrigger.TEST, "TEST-" + id, Urgency.LOW, SubjectRef.none(),
                "Test notification", correlationId, clock.instant());
        notifications.create(notification);  // the dedup seed is unique, so this always inserts
        return Optional.of(deliveryService.deliver(notification, channel.get().address()));
    }

    private void dispatchToRecipient(TriggerEvent event, RecipientPreferences recipient, LocalTime now) {
        DeliverabilityPolicy.Decision decision = deliverability.evaluate(recipient, event.urgency(), now);
        if (decision == DeliverabilityPolicy.Decision.BELOW_THRESHOLD) {
            return;
        }
        Optional<ChannelPreference> channel = recipient.preferredChannel();
        if (channel.isEmpty()) {
            return;
        }

        UUID id = idGenerator.generate(clock.instant());
        Notification notification = Notification.open(id, recipient.userId(), channel.get().channel(),
                event.trigger(), event.dedupSeed(), event.urgency(), event.refs(), event.subject(),
                event.correlationId(), clock.instant());
        if (!notifications.create(notification)) {
            log.debug("Recipient {} already notified for dedup key {}; skipping",
                    recipient.userId(), event.dedupSeed());
            return;
        }

        if (decision == DeliverabilityPolicy.Decision.QUIET_HOURS) {
            notifications.update(notification.suppressed());
            log.debug("Held notification {} for recipient {} — quiet hours",
                    id, recipient.userId());
            return;
        }
        deliveryService.deliver(notification, channel.get().address());
    }
}
