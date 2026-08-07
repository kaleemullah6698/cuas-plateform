package pk.nust.cuas.reporting.domain.port;

import pk.nust.cuas.reporting.domain.model.ReportJob;

/**
 * Publishes report lifecycle events (ARCHITECTURE.md §4.1.10, §5.2): {@code report.generation.started},
 * {@code report.generated} and {@code report.generation.failed}. An output port implemented by the
 * Kafka adapter.
 */
public interface ReportEventPublisher {

    void publishStarted(ReportJob job);

    void publishGenerated(ReportJob job);

    void publishFailed(ReportJob job);
}
