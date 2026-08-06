package pk.nust.cuas.threat.infrastructure.persistence.repository;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import pk.nust.cuas.threat.infrastructure.persistence.entity.ThreatScoreEntity;

/** Spring Data repository for {@code threat_scores}, the current level per track. */
public interface ThreatScoreJpaRepository extends JpaRepository<ThreatScoreEntity, UUID> {
}
