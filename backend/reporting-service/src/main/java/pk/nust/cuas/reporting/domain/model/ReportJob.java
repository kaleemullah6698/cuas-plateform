package pk.nust.cuas.reporting.domain.model;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * One report generation (ARCHITECTURE.md §4.1.10). The aggregate root.
 *
 * <p>Immutable (CLAUDE.md §3): the async worker advances the job through its lifecycle by returning
 * new instances — opened {@code PENDING} by the accepting request, {@code running} when the worker
 * picks it up, then {@code succeeded} with an artifact or {@code failed} with a reason. The 202
 * returns the {@code PENDING} job; generation never blocks the request thread.
 */
public final class ReportJob {

    private final UUID reportId;
    private final ReportType type;
    private final ReportFormat format;
    private final String parametersJson;
    private final String requestedBy;
    private final ReportStatus status;
    private final Optional<ReportFailureReason> failureReason;
    private final Optional<String> detail;
    private final long rowCount;
    private final Optional<Integer> generationMs;
    private final Optional<ArtifactRef> artifact;
    private final String correlationId;
    private final Instant createdAt;
    private final Optional<Instant> startedAt;
    private final Optional<Instant> completedAt;

    private ReportJob(UUID reportId, ReportType type, ReportFormat format, String parametersJson,
                      String requestedBy, ReportStatus status, Optional<ReportFailureReason> failureReason,
                      Optional<String> detail, long rowCount, Optional<Integer> generationMs,
                      Optional<ArtifactRef> artifact, String correlationId, Instant createdAt,
                      Optional<Instant> startedAt, Optional<Instant> completedAt) {
        this.reportId = reportId;
        this.type = type;
        this.format = format;
        this.parametersJson = parametersJson;
        this.requestedBy = requestedBy;
        this.status = status;
        this.failureReason = failureReason;
        this.detail = detail;
        this.rowCount = rowCount;
        this.generationMs = generationMs;
        this.artifact = artifact;
        this.correlationId = correlationId;
        this.createdAt = createdAt;
        this.startedAt = startedAt;
        this.completedAt = completedAt;
    }

    /** Opens a new accepted job. */
    public static ReportJob open(UUID reportId, ReportType type, ReportFormat format,
                                 String parametersJson, String requestedBy, String correlationId,
                                 Instant createdAt) {
        return new ReportJob(reportId, type, format, parametersJson, requestedBy, ReportStatus.PENDING,
                Optional.empty(), Optional.empty(), 0L, Optional.empty(), Optional.empty(), correlationId,
                createdAt, Optional.empty(), Optional.empty());
    }

    /** Reconstitutes from storage. */
    public static ReportJob reconstitute(UUID reportId, ReportType type, ReportFormat format,
            String parametersJson, String requestedBy, ReportStatus status,
            ReportFailureReason failureReason, String detail, long rowCount, Integer generationMs,
            ArtifactRef artifact, String correlationId, Instant createdAt, Instant startedAt,
            Instant completedAt) {
        return new ReportJob(reportId, type, format, parametersJson, requestedBy, status,
                Optional.ofNullable(failureReason), Optional.ofNullable(detail), rowCount,
                Optional.ofNullable(generationMs), Optional.ofNullable(artifact), correlationId,
                createdAt, Optional.ofNullable(startedAt), Optional.ofNullable(completedAt));
    }

    /** The worker has picked the job up. */
    public ReportJob running(Instant at) {
        return new ReportJob(reportId, type, format, parametersJson, requestedBy, ReportStatus.RUNNING,
                failureReason, detail, rowCount, generationMs, artifact, correlationId, createdAt,
                Optional.of(at), completedAt);
    }

    /** The report was generated and stored. */
    public ReportJob succeeded(ArtifactRef artifact, int generationMs, Instant at) {
        return new ReportJob(reportId, type, format, parametersJson, requestedBy, ReportStatus.SUCCEEDED,
                Optional.empty(), Optional.empty(), artifact.rowCount(), Optional.of(generationMs),
                Optional.of(artifact), correlationId, createdAt, startedAt, Optional.of(at));
    }

    /** The generation failed. */
    public ReportJob failed(ReportFailureReason reason, String detail, Instant at) {
        return new ReportJob(reportId, type, format, parametersJson, requestedBy, ReportStatus.FAILED,
                Optional.of(reason), Optional.ofNullable(detail), rowCount, generationMs, artifact,
                correlationId, createdAt, startedAt, Optional.of(at));
    }

    public UUID reportId() {
        return reportId;
    }

    public ReportType type() {
        return type;
    }

    public ReportFormat format() {
        return format;
    }

    public String parametersJson() {
        return parametersJson;
    }

    public String requestedBy() {
        return requestedBy;
    }

    public ReportStatus status() {
        return status;
    }

    public Optional<ReportFailureReason> failureReason() {
        return failureReason;
    }

    public Optional<String> detail() {
        return detail;
    }

    public long rowCount() {
        return rowCount;
    }

    public Optional<Integer> generationMs() {
        return generationMs;
    }

    public Optional<ArtifactRef> artifact() {
        return artifact;
    }

    public String correlationId() {
        return correlationId;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Optional<Instant> startedAt() {
        return startedAt;
    }

    public Optional<Instant> completedAt() {
        return completedAt;
    }
}
