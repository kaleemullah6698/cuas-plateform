package pk.nust.cuas.reporting.application.generator;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;
import pk.nust.cuas.reporting.application.ReportParams;
import pk.nust.cuas.reporting.application.port.ReportGenerator;
import pk.nust.cuas.reporting.domain.model.ProjectedEventTypes;
import pk.nust.cuas.reporting.domain.model.ReportDocument;
import pk.nust.cuas.reporting.domain.model.ReportDocument.ReportTable;
import pk.nust.cuas.reporting.domain.model.ReportEvent;
import pk.nust.cuas.reporting.domain.model.ReportJob;
import pk.nust.cuas.reporting.domain.model.ReportType;
import pk.nust.cuas.reporting.domain.port.ReportEventStore;

/**
 * A day's activity, counted by outcome (ARCHITECTURE.md §4.1.10). Takes a {@code date} (or an explicit
 * {@code from}/{@code to} range) and tallies the projected facts that fell in it.
 */
@Component
public class DailySummaryReportGenerator implements ReportGenerator {

    private final ReportEventStore events;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public DailySummaryReportGenerator(ReportEventStore events, ObjectMapper objectMapper, Clock clock) {
        this.events = events;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Override
    public ReportType type() {
        return ReportType.DAILY_SUMMARY;
    }

    @Override
    public ReportDocument generate(ReportJob job) {
        ReportParams.Range range = ReportParams.parse(objectMapper, job.parametersJson()).requireRange();
        List<ReportEvent> found = events.between(range.from(), range.to());

        Map<String, Long> counts = found.stream()
                .collect(Collectors.groupingBy(ReportEvent::eventType, TreeMap::new, Collectors.counting()));

        List<List<String>> rows = List.of(
                metric("Tracks opened", counts, ProjectedEventTypes.TRACK_CREATED),
                metric("Alerts raised", counts, ProjectedEventTypes.ALERT_RAISED),
                metric("Alerts resolved", counts, ProjectedEventTypes.ALERT_RESOLVED),
                metric("Incidents declared", counts, ProjectedEventTypes.INCIDENT_CREATED),
                metric("Incidents closed", counts, ProjectedEventTypes.INCIDENT_CLOSED));

        ReportTable table = new ReportTable("Activity", List.of("Metric", "Count"), rows);
        return new ReportDocument("Daily Summary — " + range.from() + " to " + range.to(),
                clock.instant(), List.of(table), found.size());
    }

    private static List<String> metric(String label, Map<String, Long> counts, String eventType) {
        return List.of(label, String.valueOf(counts.getOrDefault(eventType, 0L)));
    }
}
