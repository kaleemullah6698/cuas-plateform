package pk.nust.cuas.notification.infrastructure.persistence;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import pk.nust.cuas.notification.config.NotificationProperties;
import pk.nust.cuas.notification.domain.model.Channel;
import pk.nust.cuas.notification.domain.model.DeliveryFailureReason;
import pk.nust.cuas.notification.domain.model.DeliveryResult;
import pk.nust.cuas.notification.domain.model.Notification;
import pk.nust.cuas.notification.domain.model.NotificationStatus;
import pk.nust.cuas.notification.domain.model.NotificationTrigger;
import pk.nust.cuas.notification.domain.model.SubjectRef;
import pk.nust.cuas.notification.domain.model.Urgency;
import pk.nust.cuas.notification.domain.port.NotificationRepository;
import pk.nust.cuas.notification.infrastructure.persistence.entity.DeliveryReceiptEntity;
import pk.nust.cuas.notification.infrastructure.persistence.entity.NotificationLogEntity;
import pk.nust.cuas.notification.infrastructure.persistence.repository.DeliveryReceiptJpaRepository;
import pk.nust.cuas.notification.infrastructure.persistence.repository.NotificationLogJpaRepository;

/**
 * JPA implementation of {@link NotificationRepository} over {@code notifydb}.
 *
 * <p>Insertion goes through {@link NotificationInsertOperation} so the deduplication race rolls back
 * in isolation and is reported, not thrown. The retry claim leases each due row by pushing its next
 * attempt out, under {@code SKIP LOCKED}, so concurrent sweeps never process the same notification.
 */
@Repository
public class JpaNotificationRepository implements NotificationRepository {

    private final NotificationLogJpaRepository log;
    private final DeliveryReceiptJpaRepository receipts;
    private final NotificationInsertOperation inserter;
    private final Clock clock;
    private final java.time.Duration lease;

    public JpaNotificationRepository(NotificationLogJpaRepository log,
                                     DeliveryReceiptJpaRepository receipts,
                                     NotificationInsertOperation inserter,
                                     NotificationProperties properties, Clock clock) {
        this.log = log;
        this.receipts = receipts;
        this.inserter = inserter;
        this.clock = clock;
        this.lease = properties.retry().lease();
    }

    @Override
    public boolean create(Notification notification) {
        try {
            inserter.insert(toEntity(notification));
            return true;
        } catch (DataIntegrityViolationException ex) {
            return false;
        }
    }

    @Override
    @Transactional
    public void update(Notification notification) {
        apply(load(notification.notificationId()), notification, clock.instant());
    }

    @Override
    @Transactional
    public void recordDelivery(Notification notification, DeliveryResult result, Instant at) {
        apply(load(notification.notificationId()), notification, at);
        receipts.save(toReceipt(notification, result, at));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Notification> findById(UUID id) {
        return log.findById(id).map(JpaNotificationRepository::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Notification> history(UUID recipientUserId, int limit) {
        return log.findByRecipientUserIdOrderByCreatedAtDesc(recipientUserId, PageRequest.of(0, limit))
                .stream().map(JpaNotificationRepository::toDomain).toList();
    }

    @Override
    @Transactional
    public List<Notification> claimRetryable(Instant now, int limit) {
        List<NotificationLogEntity> due = log.findDueForRetry(now, PageRequest.of(0, limit));
        Instant leaseUntil = now.plus(lease);
        due.forEach(entity -> entity.leaseRetry(leaseUntil));
        log.saveAll(due);
        return due.stream().map(JpaNotificationRepository::toDomain).toList();
    }

    private NotificationLogEntity load(UUID id) {
        return log.findById(id).orElseThrow(
                () -> new IllegalStateException("notification " + id + " vanished mid-update"));
    }

    private void apply(NotificationLogEntity entity, Notification n, Instant at) {
        entity.apply(n.status().name(), n.attempt(), n.reason().map(Enum::name).orElse(null),
                n.providerMessageId().orElse(null), n.nextAttemptAt().orElse(null),
                n.sentAt().orElse(null), n.failedAt().orElse(null), at);
        log.save(entity);
    }

    private static NotificationLogEntity toEntity(Notification n) {
        return new NotificationLogEntity(n.notificationId(), n.recipientUserId(), n.channel().name(),
                n.trigger().name(), n.dedupKey(), n.urgency().name(), n.subject(), n.status().name(),
                n.attempt(), n.refs().alertId().orElse(null), n.refs().incidentId().orElse(null),
                n.refs().trackId().orElse(null), n.correlationId(), n.createdAt());
    }

    private static DeliveryReceiptEntity toReceipt(Notification n, DeliveryResult result, Instant at) {
        return switch (result) {
            case DeliveryResult.Sent sent -> new DeliveryReceiptEntity(n.notificationId(), n.attempt(),
                    "SENT", n.channel().name(), sent.providerMessageId().orElse(null), null, null,
                    (int) sent.latencyMs(), at);
            case DeliveryResult.Failed failed -> new DeliveryReceiptEntity(n.notificationId(), n.attempt(),
                    "FAILED", n.channel().name(), null, failed.reason().name(), failed.detail(), null, at);
        };
    }

    private static Notification toDomain(NotificationLogEntity e) {
        SubjectRef refs = new SubjectRef(Optional.ofNullable(e.alertId()),
                Optional.ofNullable(e.incidentId()), Optional.ofNullable(e.trackId()));
        DeliveryFailureReason reason = e.reason() == null ? null : DeliveryFailureReason.valueOf(e.reason());
        return Notification.reconstitute(e.notificationId(), e.recipientUserId(),
                Channel.valueOf(e.channel()), NotificationTrigger.valueOf(e.trigger()), e.dedupKey(),
                Urgency.valueOf(e.urgency()), refs, e.subject(), e.correlationId(),
                NotificationStatus.valueOf(e.status()), e.attempt(), e.createdAt(), e.sentAt(),
                e.failedAt(), e.nextAttemptAt(), reason, e.providerMessageId());
    }
}
