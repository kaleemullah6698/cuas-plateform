package pk.nust.cuas.notification.domain.port;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import pk.nust.cuas.notification.domain.model.DeliveryResult;
import pk.nust.cuas.notification.domain.model.Notification;

/**
 * Persistence for notifications and their delivery receipts (ARCHITECTURE.md section 4.1.9,
 * {@code notifydb}). Declared in the domain as a port; the JPA implementation is an outward adapter.
 */
public interface NotificationRepository {

    /**
     * Inserts a new notification, reporting whether it was accepted. Returns {@code false} when the
     * {@code (recipient, dedupKey)} uniqueness already holds — the recipient has already been
     * notified about this thing, so cross-channel deduplication drops it rather than paging twice.
     *
     * @param notification the pending notification
     * @return {@code true} if inserted, {@code false} if a duplicate
     */
    boolean create(Notification notification);

    /** Persists a non-delivery transition (a suppression) onto an existing notification. */
    void update(Notification notification);

    /**
     * Persists a delivery attempt's outcome as one unit: the notification's advanced status and a
     * receipt row recording the attempt.
     *
     * @param notification the notification after the attempt
     * @param result       the attempt's outcome
     * @param at           when it happened
     */
    void recordDelivery(Notification notification, DeliveryResult result, Instant at);

    /**
     * @param id a notification id
     * @return the notification, or empty if none exists
     */
    Optional<Notification> findById(UUID id);

    /**
     * A recipient's notification history, newest first.
     *
     * @param recipientUserId the recipient
     * @param limit           the maximum number to return
     * @return the notifications
     */
    List<Notification> history(UUID recipientUserId, int limit);

    /**
     * Claims retryable notifications whose next attempt is due, skipping rows another instance is
     * already retrying, so the sweep is safe to run on every instance at once.
     *
     * @param now   the sweep time
     * @param limit the maximum to claim in one sweep
     * @return the claimed notifications
     */
    List<Notification> claimRetryable(Instant now, int limit);
}
