package pk.nust.cuas.reporting.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;

/** JPA mapping for {@code report_jobs} — one report generation and its lifecycle. */
@Entity
@Table(name = "report_jobs")
public class ReportJobEntity {

    @Id
    @Column(name = "report_id")
    private UUID reportId;

    @Column(name = "type", nullable = false)
    private String type;

    @Column(name = "format", nullable = false)
    private String format;

    @Column(name = "parameters_json", nullable = false)
    private String parametersJson;

    @Column(name = "requested_by", nullable = false)
    private String requestedBy;

    @Column(name = "status", nullable = false)
    private String status;

    @Column(name = "failure_reason")
    private String failureReason;

    @Column(name = "detail")
    private String detail;

    @Column(name = "row_count", nullable = false)
    private long rowCount;

    @Column(name = "generation_ms")
    private Integer generationMs;

    @Column(name = "correlation_id")
    private String correlationId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Version
    @Column(name = "opt_version", nullable = false)
    private long optVersion;

    protected ReportJobEntity() {
    }

    public ReportJobEntity(UUID reportId, String type, String format, String parametersJson,
                           String requestedBy, String status, String correlationId, Instant createdAt) {
        this.reportId = reportId;
        this.type = type;
        this.format = format;
        this.parametersJson = parametersJson;
        this.requestedBy = requestedBy;
        this.status = status;
        this.correlationId = correlationId;
        this.createdAt = createdAt;
    }

    /** Applies a lifecycle transition. */
    public void apply(String status, String failureReason, String detail, long rowCount,
                      Integer generationMs, Instant startedAt, Instant completedAt) {
        this.status = status;
        this.failureReason = failureReason;
        this.detail = detail;
        this.rowCount = rowCount;
        this.generationMs = generationMs;
        this.startedAt = startedAt;
        this.completedAt = completedAt;
    }

    public UUID reportId() {
        return reportId;
    }

    public String type() {
        return type;
    }

    public String format() {
        return format;
    }

    public String parametersJson() {
        return parametersJson;
    }

    public String requestedBy() {
        return requestedBy;
    }

    public String status() {
        return status;
    }

    public String failureReason() {
        return failureReason;
    }

    public String detail() {
        return detail;
    }

    public long rowCount() {
        return rowCount;
    }

    public Integer generationMs() {
        return generationMs;
    }

    public String correlationId() {
        return correlationId;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant startedAt() {
        return startedAt;
    }

    public Instant completedAt() {
        return completedAt;
    }
}
