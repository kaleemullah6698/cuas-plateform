package pk.nust.cuas.reporting.domain.port;

import java.util.List;
import pk.nust.cuas.reporting.domain.model.ReportTemplate;

/** The report template catalogue (ARCHITECTURE.md §4.1.10), as listed by {@code GET /reports/templates}. */
public interface ReportTemplateRepository {

    List<ReportTemplate> all();
}
