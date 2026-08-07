package pk.nust.cuas.reporting.infrastructure.persistence;

import java.time.Clock;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import pk.nust.cuas.reporting.domain.model.ArtifactRef;
import pk.nust.cuas.reporting.domain.model.ReportFailureReason;
import pk.nust.cuas.reporting.domain.model.ReportFormat;
import pk.nust.cuas.reporting.domain.model.ReportJob;
import pk.nust.cuas.reporting.domain.model.ReportStatus;
import pk.nust.cuas.reporting.domain.model.ReportType;
import pk.nust.cuas.reporting.domain.port.ReportJobRepository;
import pk.nust.cuas.reporting.infrastructure.persistence.entity.ReportArtifactEntity;
import pk.nust.cuas.reporting.infrastructure.persistence.entity.ReportJobEntity;
import pk.nust.cuas.reporting.infrastructure.persistence.repository.ReportArtifactJpaRepository;
import pk.nust.cuas.reporting.infrastructure.persistence.repository.ReportJobJpaRepository;

/** JPA implementation of {@link ReportJobRepository} over {@code reportdb}. */
@Repository
public class JpaReportJobRepository implements ReportJobRepository {

    private final ReportJobJpaRepository jobs;
    private final ReportArtifactJpaRepository artifacts;
    private final Clock clock;

    public JpaReportJobRepository(ReportJobJpaRepository jobs, ReportArtifactJpaRepository artifacts,
                                  Clock clock) {
        this.jobs = jobs;
        this.artifacts = artifacts;
        this.clock = clock;
    }

    @Override
    @Transactional
    public void save(ReportJob job) {
        jobs.save(new ReportJobEntity(job.reportId(), job.type().name(), job.format().name(),
                job.parametersJson(), job.requestedBy(), job.status().name(), job.correlationId(),
                job.createdAt()));
    }

    @Override
    @Transactional
    public void update(ReportJob job) {
        ReportJobEntity entity = jobs.findById(job.reportId()).orElseThrow(
                () -> new IllegalStateException("report job " + job.reportId() + " vanished mid-update"));
        entity.apply(job.status().name(), job.failureReason().map(Enum::name).orElse(null),
                job.detail().orElse(null), job.rowCount(), job.generationMs().orElse(null),
                job.startedAt().orElse(null), job.completedAt().orElse(null));
        jobs.save(entity);
        job.artifact().ifPresent(artifact -> artifacts.save(new ReportArtifactEntity(job.reportId(),
                artifact.uri(), artifact.sha256(), artifact.sizeBytes(), artifact.format().name(),
                clock.instant())));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<ReportJob> findById(UUID reportId) {
        return jobs.findById(reportId)
                .map(entity -> toDomain(entity, artifacts.findById(reportId)));
    }

    private static ReportJob toDomain(ReportJobEntity e, Optional<ReportArtifactEntity> artifact) {
        ArtifactRef ref = artifact.map(a -> new ArtifactRef(a.uri(), a.sha256(), a.sizeBytes(),
                e.rowCount(), ReportFormat.valueOf(a.format()))).orElse(null);
        ReportFailureReason reason = e.failureReason() == null
                ? null : ReportFailureReason.valueOf(e.failureReason());
        return ReportJob.reconstitute(e.reportId(), ReportType.valueOf(e.type()),
                ReportFormat.valueOf(e.format()), e.parametersJson(), e.requestedBy(),
                ReportStatus.valueOf(e.status()), reason, e.detail(), e.rowCount(), e.generationMs(),
                ref, e.correlationId(), e.createdAt(), e.startedAt(), e.completedAt());
    }
}
