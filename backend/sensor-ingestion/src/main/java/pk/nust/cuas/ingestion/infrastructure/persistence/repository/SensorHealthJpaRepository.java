package pk.nust.cuas.ingestion.infrastructure.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import pk.nust.cuas.ingestion.infrastructure.persistence.entity.SensorHealthEntity;

/** Spring Data repository for {@code sensor_health}. */
public interface SensorHealthJpaRepository extends JpaRepository<SensorHealthEntity, String> {
}
