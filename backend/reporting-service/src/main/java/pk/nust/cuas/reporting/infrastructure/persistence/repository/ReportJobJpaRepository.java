package pk.nust.cuas.reporting.infrastructure.persistence.repository;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import pk.nust.cuas.reporting.infrastructure.persistence.entity.ReportJobEntity;

/** Spring Data repository for {@code report_jobs}. */
public interface ReportJobJpaRepository extends JpaRepository<ReportJobEntity, UUID> {
}
