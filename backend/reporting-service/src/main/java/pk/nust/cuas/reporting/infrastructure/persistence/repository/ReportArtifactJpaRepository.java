package pk.nust.cuas.reporting.infrastructure.persistence.repository;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import pk.nust.cuas.reporting.infrastructure.persistence.entity.ReportArtifactEntity;

/** Spring Data repository for {@code report_artifacts}. */
public interface ReportArtifactJpaRepository extends JpaRepository<ReportArtifactEntity, UUID> {
}
