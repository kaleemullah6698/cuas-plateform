package pk.nust.cuas.reporting.infrastructure.messaging;

import com.google.protobuf.Timestamp;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import pk.nust.cuas.reporting.domain.model.ArtifactRef;
import pk.nust.cuas.reporting.domain.model.ReportFailureReason;
import pk.nust.cuas.reporting.domain.model.ReportFormat;
import pk.nust.cuas.reporting.domain.model.ReportJob;
import pk.nust.cuas.reporting.domain.model.ReportType;
import pk.nust.cuas.reporting.domain.port.ReportEventPublisher;
import pk.nust.cuas.report.v1.ReportGenerated;
import pk.nust.cuas.report.v1.ReportGenerationFailed;
import pk.nust.cuas.report.v1.ReportGenerationStarted;

/**
 * Kafka adapter publishing the three report lifecycle events (ARCHITECTURE.md §4.1.10, §5.2), keyed by
 * report id so a report's events stay ordered on their partition.
 *
 * <p>Best-effort: the job's state in {@code reportdb} is the durable truth and a lost event is a
 * reporting gap, not a lost report, so a send failure is logged and dropped.
 */
@Component
public class ReportEventKafkaPublisher implements ReportEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(ReportEventKafkaPublisher.class);
    private static final String SCHEMA_VERSION = "1.0.0";

    private final KafkaTemplate<String, byte[]> kafka;

    public ReportEventKafkaPublisher(KafkaTemplate<String, byte[]> kafka) {
        this.kafka = kafka;
    }

    @Override
    public void publishStarted(ReportJob job) {
        ReportGenerationStarted event = ReportGenerationStarted.newBuilder()
                .setReportId(job.reportId().toString())
                .setType(type(job.type()))
                .setFormat(format(job.format()))
                .setRequestedBy(job.requestedBy())
                .setParametersJson(job.parametersJson())
                .setStartedAt(toTimestamp(job.createdAt()))
                .setSchemaVersion(SCHEMA_VERSION)
                .setCorrelationId(job.correlationId())
                .build();
        send(ReportTopics.REPORT_STARTED, job, event.toByteArray());
    }

    @Override
    public void publishGenerated(ReportJob job) {
        ArtifactRef artifact = job.artifact().orElseThrow(
                () -> new IllegalStateException("a generated report must have an artifact"));
        ReportGenerated event = ReportGenerated.newBuilder()
                .setReportId(job.reportId().toString())
                .setType(type(job.type()))
                .setFormat(format(job.format()))
                .setRequestedBy(job.requestedBy())
                .setArtifactUri(artifact.uri())
                .setSha256(artifact.sha256())
                .setSizeBytes(artifact.sizeBytes())
                .setRowCount(artifact.rowCount())
                .setGenerationMs(job.generationMs().orElse(0))
                .setGeneratedAt(toTimestamp(job.completedAt().orElse(job.createdAt())))
                .setSchemaVersion(SCHEMA_VERSION)
                .setCorrelationId(job.correlationId())
                .build();
        send(ReportTopics.REPORT_GENERATED, job, event.toByteArray());
    }

    @Override
    public void publishFailed(ReportJob job) {
        ReportGenerationFailed event = ReportGenerationFailed.newBuilder()
                .setReportId(job.reportId().toString())
                .setType(type(job.type()))
                .setRequestedBy(job.requestedBy())
                .setReason(reason(job.failureReason().orElse(ReportFailureReason.RENDER_ERROR)))
                .setDetail(job.detail().orElse(""))
                .setFailedAt(toTimestamp(job.completedAt().orElse(job.createdAt())))
                .setSchemaVersion(SCHEMA_VERSION)
                .setCorrelationId(job.correlationId())
                .build();
        send(ReportTopics.REPORT_FAILED, job, event.toByteArray());
    }

    private void send(String topic, ReportJob job, byte[] payload) {
        kafka.send(topic, job.reportId().toString(), payload).whenComplete((result, ex) -> {
            if (ex != null) {
                log.warn("Could not publish {} for report {}", topic, job.reportId(), ex);
            }
        });
    }

    private static pk.nust.cuas.report.v1.ReportType type(ReportType type) {
        return switch (type) {
            case INCIDENT -> pk.nust.cuas.report.v1.ReportType.REPORT_TYPE_INCIDENT;
            case DAILY_SUMMARY -> pk.nust.cuas.report.v1.ReportType.REPORT_TYPE_DAILY_SUMMARY;
            case SENSOR_PERFORMANCE -> pk.nust.cuas.report.v1.ReportType.REPORT_TYPE_SENSOR_PERFORMANCE;
            case FUSION_EFFICACY -> pk.nust.cuas.report.v1.ReportType.REPORT_TYPE_FUSION_EFFICACY;
        };
    }

    private static pk.nust.cuas.report.v1.ReportFormat format(ReportFormat format) {
        return switch (format) {
            case PDF -> pk.nust.cuas.report.v1.ReportFormat.REPORT_FORMAT_PDF;
            case JSON -> pk.nust.cuas.report.v1.ReportFormat.REPORT_FORMAT_JSON;
            case CSV -> pk.nust.cuas.report.v1.ReportFormat.REPORT_FORMAT_CSV;
        };
    }

    private static pk.nust.cuas.report.v1.ReportFailureReason reason(ReportFailureReason reason) {
        return switch (reason) {
            case NO_DATA -> pk.nust.cuas.report.v1.ReportFailureReason.REPORT_FAILURE_REASON_NO_DATA;
            case INVALID_RANGE ->
                    pk.nust.cuas.report.v1.ReportFailureReason.REPORT_FAILURE_REASON_INVALID_RANGE;
            case SOURCE_ERROR ->
                    pk.nust.cuas.report.v1.ReportFailureReason.REPORT_FAILURE_REASON_SOURCE_ERROR;
            case RENDER_ERROR ->
                    pk.nust.cuas.report.v1.ReportFailureReason.REPORT_FAILURE_REASON_RENDER_ERROR;
        };
    }

    private static Timestamp toTimestamp(Instant instant) {
        return Timestamp.newBuilder()
                .setSeconds(instant.getEpochSecond())
                .setNanos(instant.getNano())
                .build();
    }
}
