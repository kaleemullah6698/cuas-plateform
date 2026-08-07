package pk.nust.cuas.reporting.domain.model;

import java.util.Set;

/**
 * A report the service can generate (ARCHITECTURE.md §4.1.10), as listed by {@code GET /reports/templates}.
 *
 * @param type        the report type
 * @param name        its operator-facing name
 * @param description what it contains
 * @param formats     the export formats it supports
 */
public record ReportTemplate(ReportType type, String name, String description, Set<ReportFormat> formats) {

    public ReportTemplate {
        if (type == null || name == null || description == null || formats == null) {
            throw new IllegalArgumentException("all fields are required");
        }
        formats = Set.copyOf(formats);
    }
}
