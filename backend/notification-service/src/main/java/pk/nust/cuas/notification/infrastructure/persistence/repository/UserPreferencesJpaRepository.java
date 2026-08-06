package pk.nust.cuas.notification.infrastructure.persistence.repository;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import pk.nust.cuas.notification.infrastructure.persistence.entity.UserPreferencesEntity;

/** Spring Data repository for {@code user_preferences}. */
public interface UserPreferencesJpaRepository extends JpaRepository<UserPreferencesEntity, UUID> {
}
