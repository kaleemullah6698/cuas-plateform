package pk.nust.cuas.notification.infrastructure.persistence.repository;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import pk.nust.cuas.notification.infrastructure.persistence.entity.NotificationChannelEntity;

/** Spring Data repository for {@code notification_channels}. */
public interface NotificationChannelJpaRepository extends JpaRepository<NotificationChannelEntity, Long> {

    List<NotificationChannelEntity> findByUserId(UUID userId);

    void deleteByUserId(UUID userId);

    /** The recipients to fan out to: distinct users with at least one enabled channel. */
    @Query("select distinct c.userId from NotificationChannelEntity c where c.enabled = true")
    List<UUID> findUserIdsWithEnabledChannel();
}
