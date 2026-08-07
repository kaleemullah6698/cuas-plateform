package pk.nust.cuas.reporting.api.dto;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import pk.nust.cuas.reporting.domain.model.ReportFormat;
import pk.nust.cuas.reporting.domain.model.ReportJob;
import pk.nust.cuas.reporting.domain.model.ReportStatus;
import pk.nust.cuas.reporting.domain.model.ReportTemplate;
import pk.nust.cuas.reporting.domain.model.ReportType;

/** Request and response DTOs for the reporting API. */
public final class ReportDtos {

    private ReportDtos() {
    }

    /**
     * A request to generate a report.
     *
     * @param type       the report to generate
     * @param format     the export format
     * @param parameters the type-specific parameters (an incident id, a date, a range); may be null
     */
    public record GenerateReportRequest(@NotNull ReportType type, @NotNull ReportFormat format,
                                        JsonNode parameters) {
    }

    /**
     * A report job's state.
     *
     * @param reportId      the job id
     * @param type          the report type
     * @param format        the export format
     * @param status        its lifecycle state
     * @param failureReason why it failed, if it did
     * @param detail        failure detail, if any
     * @param rowCount      source records summarised
     * @param artifactUri   the stored artifact, once succeeded
     * @param sha256        the artifact hash
     * @param sizeBytes     the artifact size
     * @param generationMs  how long it took
     * @param createdAt     when accepted
     * @param startedAt     when generation began
     * @param completedAt   when it finished
     */
    public record ReportJobResponse(String reportId, ReportType type, ReportFormat format,
                                    ReportStatus status, String failureReason, String detail,
                                    long rowCount, String artifactUri, String sha256, Long sizeBytes,
                                    Integer generationMs, Instant createdAt, Instant startedAt,
                                    Instant completedAt) {

        public static ReportJobResponse from(ReportJob job) {
            return new ReportJobResponse(job.reportId().toString(), job.type(), job.format(),
                    job.status(), job.failureReason().map(Enum::name).orElse(null),
                    job.detail().orElse(null), job.rowCount(),
                    job.artifact().map(a -> a.uri()).orElse(null),
                    job.artifact().map(a -> a.sha256()).orElse(null),
                    job.artifact().map(a -> a.sizeBytes()).orElse(null),
                    job.generationMs().orElse(null), job.createdAt(), job.startedAt().orElse(null),
                    job.completedAt().orElse(null));
        }
    }

    /**
     * A report the service can generate.
     *
     * @param type        the report type
     * @param name        its name
     * @param description what it contains
     * @param formats     the export formats it supports
     */
    public record ReportTemplateResponse(ReportType type, String name, String description,
                                         Set<ReportFormat> formats) {

        public static ReportTemplateResponse from(ReportTemplate template) {
            return new ReportTemplateResponse(template.type(), template.name(), template.description(),
                    template.formats());
        }
    }

    /** Wraps the template list for a stable response shape. */
    public record ReportTemplatesResponse(List<ReportTemplateResponse> templates) {
    }
}
