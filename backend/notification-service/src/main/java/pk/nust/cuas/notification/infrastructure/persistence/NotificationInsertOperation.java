package pk.nust.cuas.notification.infrastructure.persistence;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import pk.nust.cuas.notification.infrastructure.persistence.entity.NotificationLogEntity;
import pk.nust.cuas.notification.infrastructure.persistence.repository.NotificationLogJpaRepository;

/**
 * Inserts a new notification row in its own transaction, so the {@code uq_notification_dedup} race is
 * lost gracefully.
 *
 * <p>A unique-constraint violation marks its transaction rollback-only; catching it inside that same
 * transaction still fails the commit. This runs under {@code REQUIRES_NEW} and does not catch — the
 * exception must leave this boundary so the inner transaction rolls back cleanly, and the caller
 * catches it outside. Separate bean because Spring's transaction proxy does not apply to a
 * self-invocation.
 */
@Component
class NotificationInsertOperation {

    private final NotificationLogJpaRepository log;

    NotificationInsertOperation(NotificationLogJpaRepository log) {
        this.log = log;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    void insert(NotificationLogEntity entity) {
        log.saveAndFlush(entity);
    }
}
