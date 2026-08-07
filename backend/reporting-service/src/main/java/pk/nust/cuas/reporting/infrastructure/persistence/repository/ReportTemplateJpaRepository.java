package pk.nust.cuas.reporting.infrastructure.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import pk.nust.cuas.reporting.infrastructure.persistence.entity.ReportTemplateEntity;

/** Spring Data repository for {@code report_templates}. */
public interface ReportTemplateJpaRepository extends JpaRepository<ReportTemplateEntity, String> {
}
