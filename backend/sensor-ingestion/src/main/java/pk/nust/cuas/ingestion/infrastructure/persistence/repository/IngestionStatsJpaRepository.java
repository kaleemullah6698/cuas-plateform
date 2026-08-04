package pk.nust.cuas.ingestion.infrastructure.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import pk.nust.cuas.ingestion.infrastructure.persistence.entity.IngestionStatsEntity;

/** Spring Data repository for {@code ingestion_stats}. */
public interface IngestionStatsJpaRepository extends JpaRepository<IngestionStatsEntity, String> {
}
