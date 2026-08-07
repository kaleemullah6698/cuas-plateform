package pk.nust.cuas.reporting.application.generator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import pk.nust.cuas.reporting.domain.model.ProjectedEventTypes;
import pk.nust.cuas.reporting.domain.model.ReportDocument;
import pk.nust.cuas.reporting.domain.model.ReportEvent;
import pk.nust.cuas.reporting.domain.model.ReportFormat;
import pk.nust.cuas.reporting.domain.model.ReportJob;
import pk.nust.cuas.reporting.domain.model.ReportType;
import pk.nust.cuas.reporting.domain.port.ReportEventStore;

/** The daily-summary generator tallies the projected facts in the requested day. */
class DailySummaryReportGeneratorTest {

    private final ReportEventStore store = mock(ReportEventStore.class);
    private final Clock clock = Clock.fixed(Instant.parse("2026-08-08T00:00:00Z"), ZoneOffset.UTC);
    private final DailySummaryReportGenerator generator =
            new DailySummaryReportGenerator(store, new ObjectMapper(), clock);

    @Test
    void itCountsEachEventTypeForTheDay() {
        Instant from = Instant.parse("2026-08-07T00:00:00Z");
        Instant to = Instant.parse("2026-08-08T00:00:00Z");
        when(store.between(from, to)).thenReturn(List.of(
                event(ProjectedEventTypes.ALERT_RAISED),
                event(ProjectedEventTypes.ALERT_RAISED),
                event(ProjectedEventTypes.TRACK_CREATED)));

        ReportDocument document = generator.generate(job("{\"date\":\"2026-08-07\"}"));

        assertThat(document.rowCount()).isEqualTo(3);
        List<List<String>> rows = document.tables().get(0).rows();
        assertThat(rows).contains(List.of("Alerts raised", "2"), List.of("Tracks opened", "1"),
                List.of("Incidents declared", "0"));
    }

    private static ReportEvent event(String type) {
        return new ReportEvent(type, Optional.of(UUID.randomUUID()),
                Instant.parse("2026-08-07T10:00:00Z"), Map.of(), type + ":" + UUID.randomUUID());
    }

    private static ReportJob job(String parameters) {
        return ReportJob.open(UUID.randomUUID(), ReportType.DAILY_SUMMARY, ReportFormat.JSON,
                parameters, "usr-1", "corr-1", Instant.parse("2026-08-08T00:00:00Z"));
    }
}
