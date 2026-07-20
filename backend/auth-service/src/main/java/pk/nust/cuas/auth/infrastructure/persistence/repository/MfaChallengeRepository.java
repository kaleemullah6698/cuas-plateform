package pk.nust.cuas.auth.infrastructure.persistence.repository;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import pk.nust.cuas.auth.infrastructure.persistence.entity.MfaChallengeEntity;

/** Short-lived proof that the first factor succeeded. */
public interface MfaChallengeRepository extends JpaRepository<MfaChallengeEntity, UUID> {
}
