package pk.nust.cuas.ingestion.infrastructure.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import pk.nust.cuas.ingestion.infrastructure.persistence.entity.SensorEntity;

/** Spring Data repository for the {@code sensors} registry. */
public interface SensorJpaRepository extends JpaRepository<SensorEntity, String> {
}
