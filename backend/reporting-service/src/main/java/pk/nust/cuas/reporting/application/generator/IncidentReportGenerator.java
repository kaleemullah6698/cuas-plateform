package pk.nust.cuas.reporting.application.generator;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;
import pk.nust.cuas.reporting.application.ReportDataException;
import pk.nust.cuas.reporting.application.ReportParams;
import pk.nust.cuas.reporting.application.port.ReportGenerator;
import pk.nust.cuas.reporting.domain.model.ReportDocument;
import pk.nust.cuas.reporting.domain.model.ReportDocument.ReportTable;
import pk.nust.cuas.reporting.domain.model.ReportEvent;
import pk.nust.cuas.reporting.domain.model.ReportJob;
import pk.nust.cuas.reporting.domain.model.ReportType;
import pk.nust.cuas.reporting.domain.port.ReportEventStore;

/**
 * One incident's timeline (ARCHITECTURE.md §4.1.10): every projected fact about the incident id, in
 * order. Requires an {@code incidentId}; an incident with no projected facts is a {@link
 * ReportDataException#noData} rather than an empty report.
 */
@Component
public class IncidentReportGenerator implements ReportGenerator {

    private final ReportEventStore events;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public IncidentReportGenerator(ReportEventStore events, ObjectMapper objectMapper, Clock clock) {
        this.events = events;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Override
    public ReportType type() {
        return ReportType.INCIDENT;
    }

    @Override
    public ReportDocument generate(ReportJob job) {
        UUID incidentId = ReportParams.parse(objectMapper, job.parametersJson()).requireUuid("incidentId");
        List<ReportEvent> timeline = events.forSubject(incidentId).stream()
                .sorted(Comparator.comparing(ReportEvent::occurredAt))
                .toList();
        if (timeline.isEmpty()) {
            throw ReportDataException.noData("no events recorded for incident " + incidentId);
        }

        List<List<String>> rows = timeline.stream()
                .map(event -> List.of(event.occurredAt().toString(), event.eventType(),
                        event.attributes().toString()))
                .toList();
        ReportTable table = new ReportTable("Timeline",
                List.of("Occurred At", "Event", "Detail"), rows);
        return new ReportDocument("Incident Report — " + incidentId, clock.instant(),
                List.of(table), timeline.size());
    }
}
