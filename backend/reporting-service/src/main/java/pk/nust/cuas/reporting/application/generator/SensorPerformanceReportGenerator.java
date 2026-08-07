package pk.nust.cuas.reporting.application.generator;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
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
 * Per-sensor health over a range (ARCHITECTURE.md §4.1.10): the online / degraded / offline
 * transitions each sensor reported. A sensor churning between states is what this surfaces.
 */
@Component
public class SensorPerformanceReportGenerator implements ReportGenerator {

    private final ReportEventStore events;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public SensorPerformanceReportGenerator(ReportEventStore events, ObjectMapper objectMapper,
                                            Clock clock) {
        this.events = events;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Override
    public ReportType type() {
        return ReportType.SENSOR_PERFORMANCE;
    }

    @Override
    public ReportDocument generate(ReportJob job) {
        ReportParams.Range range = ReportParams.parse(objectMapper, job.parametersJson()).requireRange();
        List<ReportEvent> health =
                events.ofTypeBetween(ProjectedEventTypes.SENSOR_HEALTH, range.from(), range.to());

        // sensorId -> {status -> count}
        Map<String, Map<String, Long>> bySensor = new TreeMap<>();
        for (ReportEvent event : health) {
            String sensorId = event.attribute("sensorId").orElse("unknown");
            String status = event.attribute("status").orElse("UNKNOWN");
            bySensor.computeIfAbsent(sensorId, k -> new TreeMap<>()).merge(status, 1L, Long::sum);
        }

        List<List<String>> rows = bySensor.entrySet().stream()
                .map(entry -> List.of(entry.getKey(),
                        count(entry.getValue(), "ONLINE"),
                        count(entry.getValue(), "DEGRADED"),
                        count(entry.getValue(), "OFFLINE")))
                .toList();
        ReportTable table = new ReportTable("Sensor health transitions",
                List.of("Sensor", "Online", "Degraded", "Offline"), rows);
        return new ReportDocument("Sensor Performance — " + range.from() + " to " + range.to(),
                clock.instant(), List.of(table), health.size());
    }

    private static String count(Map<String, Long> statuses, String status) {
        return String.valueOf(statuses.getOrDefault(status, 0L));
    }
}
