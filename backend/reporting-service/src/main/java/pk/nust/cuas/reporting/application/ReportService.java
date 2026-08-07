package pk.nust.cuas.reporting.application;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Clock;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import pk.nust.cuas.commons.id.Uuidv7Generator;
import pk.nust.cuas.reporting.domain.model.ReportFormat;
import pk.nust.cuas.reporting.domain.model.ReportJob;
import pk.nust.cuas.reporting.domain.model.ReportTemplate;
import pk.nust.cuas.reporting.domain.model.ReportType;
import pk.nust.cuas.reporting.domain.port.ArtifactStore;
import pk.nust.cuas.reporting.domain.port.ReportEventPublisher;
import pk.nust.cuas.reporting.domain.port.ReportJobRepository;
import pk.nust.cuas.reporting.domain.port.ReportTemplateRepository;

/**
 * Accepts and reads report jobs (ARCHITECTURE.md §4.1.10).
 *
 * <p>Submission is synchronous only up to acceptance: the job is validated against its template,
 * persisted {@code PENDING}, its {@code started} event published, and the async worker handed the id —
 * then the 202 returns the {@code PENDING} job. Generation never blocks the request thread.
 */
@Service
public class ReportService {

    private static final Logger log = LoggerFactory.getLogger(ReportService.class);

    private final ReportTemplateRepository templates;
    private final ReportJobRepository jobs;
    private final ArtifactStore artifacts;
    private final ReportEventPublisher publisher;
    private final ReportWorker worker;
    private final Uuidv7Generator idGenerator;
    private final Clock clock;

    public ReportService(ReportTemplateRepository templates, ReportJobRepository jobs,
                         ArtifactStore artifacts, ReportEventPublisher publisher, ReportWorker worker,
                         Uuidv7Generator idGenerator, Clock clock) {
        this.templates = templates;
        this.jobs = jobs;
        this.artifacts = artifacts;
        this.publisher = publisher;
        this.worker = worker;
        this.idGenerator = idGenerator;
        this.clock = clock;
    }

    public List<ReportTemplate> templates() {
        return templates.all();
    }

    /**
     * Accepts a report request and starts its asynchronous generation.
     *
     * @return the accepted {@code PENDING} job
     * @throws IllegalArgumentException if the type is unknown or the template does not support the format
     */
    public ReportJob submit(ReportType type, ReportFormat format, JsonNode parameters,
                            String requestedBy, String correlationId) {
        ReportTemplate template = templates.all().stream()
                .filter(candidate -> candidate.type() == type)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("unknown report type " + type));
        if (!template.formats().contains(format)) {
            throw new IllegalArgumentException(type + " does not support " + format);
        }

        UUID reportId = idGenerator.generate(clock.instant());
        ReportJob job = ReportJob.open(reportId, type, format, parameters.toString(), requestedBy,
                correlationId, clock.instant());
        jobs.save(job);
        publisher.publishStarted(job);
        log.info("Accepted report {} ({}/{}) for {}", reportId, type, format, requestedBy);
        worker.process(reportId);
        return job;
    }

    public Optional<ReportJob> job(UUID reportId) {
        return jobs.findById(reportId);
    }

    /** The rendered artifact of a succeeded job, for download. */
    public Optional<Download> download(UUID reportId) {
        return jobs.findById(reportId)
                .filter(job -> job.artifact().isPresent())
                .flatMap(job -> job.artifact().flatMap(artifacts::load)
                        .map(bytes -> new Download(bytes, job.format(), filename(job))));
    }

    private static String filename(ReportJob job) {
        return job.type().name().toLowerCase() + "-" + job.reportId() + "." + job.format().extension();
    }

    /**
     * A downloadable artifact.
     *
     * @param content  the rendered bytes
     * @param format   the export format
     * @param filename a suggested download filename
     */
    public record Download(byte[] content, ReportFormat format, String filename) {
    }
}
