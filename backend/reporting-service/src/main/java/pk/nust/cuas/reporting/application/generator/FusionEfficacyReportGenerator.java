package pk.nust.cuas.reporting.application.generator;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.util.ArrayList;
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
 * How well the platform is calling threats (ARCHITECTURE.md §4.1.10): the true- vs false-positive
 * ratio from resolved-alert dispositions, and the AI mock-vs-real share from inference devices — the
 * two numbers that say whether the scoring is calibrated and how much of it is running on real models.
 */
@Component
public class FusionEfficacyReportGenerator implements ReportGenerator {

    private final ReportEventStore events;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public FusionEfficacyReportGenerator(ReportEventStore events, ObjectMapper objectMapper, Clock clock) {
        this.events = events;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Override
    public ReportType type() {
        return ReportType.FUSION_EFFICACY;
    }

    @Override
    public ReportDocument generate(ReportJob job) {
        ReportParams.Range range = ReportParams.parse(objectMapper, job.parametersJson()).requireRange();
        List<ReportEvent> resolved =
                events.ofTypeBetween(ProjectedEventTypes.ALERT_RESOLVED, range.from(), range.to());
        List<ReportEvent> inferences =
                events.ofTypeBetween(ProjectedEventTypes.AI_INFERENCE, range.from(), range.to());

        Map<String, Long> byDisposition = resolved.stream()
                .collect(Collectors.groupingBy(e -> e.attribute("disposition").orElse("UNSPECIFIED"),
                        TreeMap::new, Collectors.counting()));
        List<List<String>> dispositionRows = new ArrayList<>();
        byDisposition.forEach((disposition, count) ->
                dispositionRows.add(List.of(disposition, String.valueOf(count))));
        long truePositive = byDisposition.getOrDefault("TRUE_POSITIVE", 0L);
        long falsePositive = byDisposition.getOrDefault("FALSE_POSITIVE", 0L);
        dispositionRows.add(List.of("True-positive ratio", ratio(truePositive, truePositive + falsePositive)));

        long mock = inferences.stream()
                .filter(e -> "DEVICE_MOCK".equals(e.attribute("device").orElse(""))).count();
        List<List<String>> aiRows = List.of(
                List.of("Inferences", String.valueOf(inferences.size())),
                List.of("On mock provider", String.valueOf(mock)),
                List.of("On a real model", String.valueOf(inferences.size() - mock)));

        List<ReportTable> tables = List.of(
                new ReportTable("Alert dispositions", List.of("Disposition", "Count"), dispositionRows),
                new ReportTable("AI mock-vs-real", List.of("Metric", "Value"), aiRows));
        return new ReportDocument("Fusion Efficacy — " + range.from() + " to " + range.to(),
                clock.instant(), tables, (long) resolved.size() + inferences.size());
    }

    private static String ratio(long numerator, long denominator) {
        return denominator == 0 ? "n/a" : String.format("%.2f", (double) numerator / denominator);
    }
}
