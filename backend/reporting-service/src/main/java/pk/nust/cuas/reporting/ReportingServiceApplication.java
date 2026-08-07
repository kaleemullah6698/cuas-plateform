package pk.nust.cuas.reporting;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Reporting-service (ARCHITECTURE.md §4.1.10): templated, asynchronously-generated reports (incident,
 * daily summary, sensor performance, fusion efficacy) exported as PDF, JSON or CSV.
 *
 * <p>Consumes the domain topics into an event-projection read model in {@code reportdb} and aggregates
 * reports over it; publishes {@code report.generation.started/generated/failed}. Elasticsearch is the
 * scale-out mirror of the projection, a documented follow-on behind the {@code ReportEventStore} port.
 */
@SpringBootApplication
public class ReportingServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(ReportingServiceApplication.class, args);
    }
}
