package pk.nust.cuas.reporting.infrastructure.persistence;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Repository;
import pk.nust.cuas.reporting.domain.model.ReportFormat;
import pk.nust.cuas.reporting.domain.model.ReportTemplate;
import pk.nust.cuas.reporting.domain.model.ReportType;
import pk.nust.cuas.reporting.domain.port.ReportTemplateRepository;
import pk.nust.cuas.reporting.infrastructure.persistence.entity.ReportTemplateEntity;
import pk.nust.cuas.reporting.infrastructure.persistence.repository.ReportTemplateJpaRepository;

/** JPA implementation of {@link ReportTemplateRepository}, reading the seeded catalogue. */
@Repository
public class JpaReportTemplateRepository implements ReportTemplateRepository {

    private final ReportTemplateJpaRepository templates;

    public JpaReportTemplateRepository(ReportTemplateJpaRepository templates) {
        this.templates = templates;
    }

    @Override
    public List<ReportTemplate> all() {
        return templates.findAll().stream().map(JpaReportTemplateRepository::toDomain).toList();
    }

    private static ReportTemplate toDomain(ReportTemplateEntity e) {
        Set<ReportFormat> formats = Arrays.stream(e.formats().split(","))
                .map(String::trim)
                .filter(token -> !token.isEmpty())
                .map(ReportFormat::valueOf)
                .collect(Collectors.toUnmodifiableSet());
        return new ReportTemplate(ReportType.valueOf(e.type()), e.name(), e.description(), formats);
    }
}
