package pk.nust.cuas.reporting.application;

import java.time.Clock;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import pk.nust.cuas.commons.correlation.CorrelationId;
import pk.nust.cuas.reporting.domain.model.ArtifactRef;
import pk.nust.cuas.reporting.domain.model.ReportDocument;
import pk.nust.cuas.reporting.domain.model.ReportFailureReason;
import pk.nust.cuas.reporting.domain.model.ReportJob;
import pk.nust.cuas.reporting.domain.port.ArtifactStore;
import pk.nust.cuas.reporting.domain.port.ReportEventPublisher;
import pk.nust.cuas.reporting.domain.port.ReportJobRepository;

/**
 * Generates a report off the request thread (ARCHITECTURE.md §4.1.10).
 *
 * <p>Runs on the {@code reportExecutor} pool: it marks the job {@code RUNNING}, generates the document
 * from the projection, renders it to the requested format, stores the artifact, and marks the job
 * {@code SUCCEEDED} with the artifact reference — publishing {@code report.generated}. A bad request
 * (unusable parameters, no data) fails the job terminally with the generator's reason; any other fault
 * is a {@code RENDER_ERROR}. A failure always leaves the job in a terminal state with a reason, never
 * stuck {@code RUNNING}, so a caller polling the job always learns the outcome.
 */
@Component
public class ReportWorker {

    private static final Logger log = LoggerFactory.getLogger(ReportWorker.class);

    private final ReportJobRepository jobs;
    private final ReportGeneratorRegistry generators;
    private final ReportRendererRegistry renderers;
    private final ArtifactStore artifacts;
    private final ReportEventPublisher publisher;
    private final Clock clock;

    public ReportWorker(ReportJobRepository jobs, ReportGeneratorRegistry generators,
                        ReportRendererRegistry renderers, ArtifactStore artifacts,
                        ReportEventPublisher publisher, Clock clock) {
        this.jobs = jobs;
        this.generators = generators;
        this.renderers = renderers;
        this.artifacts = artifacts;
        this.publisher = publisher;
        this.clock = clock;
    }

    /**
     * Generates the report for a job.
     *
     * @param reportId the job to generate
     */
    @Async("reportExecutor")
    public void process(UUID reportId) {
        ReportJob job = jobs.findById(reportId).orElse(null);
        if (job == null) {
            log.warn("Report job {} vanished before generation", reportId);
            return;
        }
        CorrelationId.set(job.correlationId() == null || job.correlationId().isBlank()
                ? CorrelationId.generate() : job.correlationId());
        try {
            generate(job);
        } finally {
            CorrelationId.clear();
        }
    }

    private void generate(ReportJob accepted) {
        ReportJob job = accepted.running(clock.instant());
        jobs.update(job);
        long start = clock.millis();
        try {
            ReportDocument document = generators.forType(job.type()).generate(job);
            byte[] rendered = renderers.forFormat(job.format()).render(document);
            ArtifactRef artifact = artifacts.store(job.reportId(), job.format(), rendered,
                    document.rowCount());
            ReportJob done = job.succeeded(artifact, (int) (clock.millis() - start), clock.instant());
            jobs.update(done);
            publisher.publishGenerated(done);
            log.info("Generated report {} ({} bytes, {} rows)", job.reportId(), artifact.sizeBytes(),
                    artifact.rowCount());
        } catch (ReportDataException ex) {
            fail(job, ex.reason(), ex.getMessage());
        } catch (RuntimeException ex) {
            log.error("Report {} generation faulted", job.reportId(), ex);
            fail(job, ReportFailureReason.RENDER_ERROR, "the report could not be generated");
        }
    }

    private void fail(ReportJob job, ReportFailureReason reason, String detail) {
        ReportJob failed = job.failed(reason, detail, clock.instant());
        jobs.update(failed);
        publisher.publishFailed(failed);
        log.warn("Report {} failed: {} — {}", job.reportId(), reason, detail);
    }
}
