package pk.nust.cuas.reporting;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import pk.nust.cuas.reporting.application.ReportService;
import pk.nust.cuas.reporting.domain.model.ProjectedEventTypes;
import pk.nust.cuas.reporting.domain.model.ReportEvent;
import pk.nust.cuas.reporting.domain.model.ReportFailureReason;
import pk.nust.cuas.reporting.domain.model.ReportFormat;
import pk.nust.cuas.reporting.domain.model.ReportJob;
import pk.nust.cuas.reporting.domain.model.ReportStatus;
import pk.nust.cuas.reporting.domain.model.ReportType;
import pk.nust.cuas.reporting.domain.port.ReportEventPublisher;
import pk.nust.cuas.reporting.domain.port.ReportEventStore;

/**
 * The projection read model and the async generate → render → store → download pipeline against a real
 * PostgreSQL (ARCHITECTURE.md §4.1.10). Against a real database because what matters is that the
 * {@code uq_report_event} constraint makes projection idempotent, the async worker drives a job to a
 * terminal state, and the Flyway migration (including the seeded templates) validates against the
 * entities. Kafka is mocked — the projection is fed directly, and the event publisher is a port.
 */
@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
class ReportingServiceIntegrationTest {

    @Container
    @SuppressWarnings("resource")
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine")
            .withDatabaseName("reportdb");

    static Path artifactRoot;

    @DynamicPropertySource
    static void infrastructure(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.kafka.listener.auto-startup", () -> "false");
        registry.add("reporting.artifact-root", () -> artifactRootDir().toString());
    }

    private static Path artifactRootDir() {
        if (artifactRoot == null) {
            try {
                artifactRoot = Files.createTempDirectory("reports-test");
            } catch (IOException ex) {
                throw new IllegalStateException(ex);
            }
        }
        return artifactRoot;
    }

    @Autowired
    private ReportService reports;

    @Autowired
    private ReportEventStore events;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbc;

    @MockitoBean
    private ReportEventPublisher publisher;

    @BeforeEach
    void freshState() {
        jdbc.execute("TRUNCATE report_jobs, report_artifacts, report_events RESTART IDENTITY CASCADE");
    }

    @Test
    void theTemplateCatalogueIsSeeded() {
        assertThat(reports.templates()).extracting(t -> t.type())
                .containsExactlyInAnyOrder(ReportType.INCIDENT, ReportType.DAILY_SUMMARY,
                        ReportType.SENSOR_PERFORMANCE, ReportType.FUSION_EFFICACY);
    }

    @Test
    void projectionIsIdempotentOnTheDedupKey() {
        ReportEvent event = new ReportEvent(ProjectedEventTypes.ALERT_RAISED,
                Optional.of(UUID.randomUUID()), Instant.parse("2026-08-07T10:00:00Z"), Map.of(),
                "alert.raised:once");

        events.append(event);
        events.append(event);   // redelivered

        assertThat(events.between(Instant.parse("2026-08-07T00:00:00Z"),
                Instant.parse("2026-08-08T00:00:00Z"))).hasSize(1);
    }

    @Test
    void aReportIsGeneratedStoredAndDownloadable() throws Exception {
        events.append(alertRaised("2026-08-07T09:00:00Z"));
        events.append(alertRaised("2026-08-07T11:00:00Z"));

        ReportJob accepted = reports.submit(ReportType.DAILY_SUMMARY, ReportFormat.JSON,
                objectMapper.readTree("{\"date\":\"2026-08-07\"}"), "usr-1", "corr-1");
        assertThat(accepted.status()).isEqualTo(ReportStatus.PENDING);

        await().atMost(Duration.ofSeconds(15)).until(() -> terminal(accepted.reportId()));

        ReportJob done = reports.job(accepted.reportId()).orElseThrow();
        assertThat(done.status()).isEqualTo(ReportStatus.SUCCEEDED);
        assertThat(done.rowCount()).isEqualTo(2);
        assertThat(done.artifact()).isPresent();

        byte[] content = reports.download(accepted.reportId()).orElseThrow().content();
        var tree = objectMapper.readTree(content);
        assertThat(tree.get("title").asText()).contains("Daily Summary");

        verify(publisher).publishStarted(org.mockito.ArgumentMatchers.any());
        verify(publisher).publishGenerated(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void anUnusableRequestFailsTheJobTerminally() {
        ReportJob accepted = reports.submit(ReportType.DAILY_SUMMARY, ReportFormat.JSON,
                objectMapper.createObjectNode(), "usr-1", "corr-1");   // no date/range

        await().atMost(Duration.ofSeconds(15)).until(() -> terminal(accepted.reportId()));

        ReportJob done = reports.job(accepted.reportId()).orElseThrow();
        assertThat(done.status()).isEqualTo(ReportStatus.FAILED);
        assertThat(done.failureReason()).contains(ReportFailureReason.INVALID_RANGE);
        verify(publisher).publishFailed(org.mockito.ArgumentMatchers.any());
    }

    private boolean terminal(UUID reportId) {
        return reports.job(reportId).map(job -> job.status().isTerminal()).orElse(false);
    }

    private static ReportEvent alertRaised(String at) {
        return new ReportEvent(ProjectedEventTypes.ALERT_RAISED, Optional.of(UUID.randomUUID()),
                Instant.parse(at), Map.of("severity", "HIGH"),
                "alert.raised:" + UUID.randomUUID());
    }
}
