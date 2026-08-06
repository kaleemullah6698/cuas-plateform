package pk.nust.cuas.notification.infrastructure.persistence.repository;

import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;
import pk.nust.cuas.notification.infrastructure.persistence.entity.NotificationLogEntity;

/** Spring Data repository for {@code notification_log}. */
public interface NotificationLogJpaRepository extends JpaRepository<NotificationLogEntity, UUID> {

    List<NotificationLogEntity> findByRecipientUserIdOrderByCreatedAtDesc(UUID recipientUserId,
                                                                          Pageable page);

    /**
     * Retryable notifications whose next attempt is due, locked for the claiming transaction and
     * skipping rows another instance already holds — {@code -2} is Hibernate's SKIP_LOCKED — so the
     * sweep is safe to run on every instance at once.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "-2"))
    @Query("select n from NotificationLogEntity n "
            + "where n.status = 'FAILED_RETRYABLE' and n.nextAttemptAt <= :now "
            + "order by n.nextAttemptAt asc")
    List<NotificationLogEntity> findDueForRetry(@Param("now") Instant now, Pageable page);
}
