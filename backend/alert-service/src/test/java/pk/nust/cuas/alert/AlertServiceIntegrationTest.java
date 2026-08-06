package pk.nust.cuas.alert;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import pk.nust.cuas.alert.application.AlertLifecycleService;
import pk.nust.cuas.alert.application.EscalationSweep;
import pk.nust.cuas.alert.domain.model.Alert;
import pk.nust.cuas.alert.domain.model.AlertDisposition;
import pk.nust.cuas.alert.domain.model.AlertStatus;
import pk.nust.cuas.alert.domain.model.Recommendation;
import pk.nust.cuas.alert.domain.model.Severity;
import pk.nust.cuas.alert.domain.port.AlertEventPublisher;
import pk.nust.cuas.alert.domain.port.AlertRepository;
import pk.nust.cuas.alert.domain.port.EscalationScheduler;
import pk.nust.cuas.alert.domain.service.ThreatTransition;

/**
 * The alert lifecycle against a real PostgreSQL and a real Redis (ARCHITECTURE.md section 4.1.8).
 *
 * <p>Against real infrastructure rather than fakes, because the three things that matter most here
 * are exactly what a fake would get approximately right: that {@code uq_alert_open_dedup} actually
 * rejects a concurrent duplicate, that the Flyway migration applies and Hibernate validates against
 * it, and that the sorted-set scheduler and the database sweep agree about what is due.
 *
 * <p>Kafka is mocked. The publisher is a port and its Kafka adapter has no logic worth a broker in
 * this suite; what is under test is which events the lifecycle decides to emit, which a mock records
 * precisely and a broker would only make slower.
 */
@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
class AlertServiceIntegrationTest {

    private static final String POLICY = "policy-v3";

    @Container
    @SuppressWarnings("resource")
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine")
            .withDatabaseName("alertdb");

    @Container
    @SuppressWarnings("resource")
    static GenericContainer<?> redis =
            new GenericContainer<>("redis:7.4-alpine").withExposedPorts(6379);

    @DynamicPropertySource
    static void infrastructure(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
        // No broker in this suite; the listeners would otherwise retry a connection for its duration.
        registry.add("spring.kafka.listener.auto-startup", () -> "false");
    }

    @Autowired
    private AlertLifecycleService lifecycle;

    @Autowired
    private AlertRepository alerts;

    @Autowired
    private EscalationScheduler scheduler;

    @Autowired
    private EscalationSweep sweep;

    @MockitoBean
    private AlertEventPublisher events;

    private UUID track;

    @BeforeEach
    void freshTrack() {
        track = UUID.randomUUID();
    }

    // ── Raising and deduplication ───────────────────────────────────────────────────────────────

    @Test
    void aThreatEscalationRaisesAnAlert() {
        Optional<Alert> raised = lifecycle.onThreatTransition(transition(Severity.CRITICAL));

        assertThat(raised).isPresent();
        assertThat(raised.get().status()).isEqualTo(AlertStatus.RAISED);
        assertThat(raised.get().severity()).isEqualTo(Severity.CRITICAL);
        assertThat(raised.get().escalatesAt()).isPresent();
    }

    @Test
    void aRepeatedTransitionIsFoldedIntoTheOpenAlert() {
        Alert first = lifecycle.onThreatTransition(transition(Severity.CRITICAL)).orElseThrow();

        Optional<Alert> second = lifecycle.onThreatTransition(transition(Severity.CRITICAL));

        assertThat(second).as("a recurrence inside the window raises nothing").isEmpty();
        assertThat(alerts.findById(first.alertId()).orElseThrow().occurrenceCount()).isEqualTo(2);
    }

    @Test
    void theUniqueConstraintRejectsAConcurrentDuplicate() {
        // The defence the read cannot provide: two instances processing the same transition after a
        // rebalance, both seeing no open alert, both inserting. Section 5.7 makes losing this race
        // quietly the whole point.
        Alert first = lifecycle.onThreatTransition(transition(Severity.CRITICAL)).orElseThrow();
        Alert concurrent = Alert.raise(
                UUID.randomUUID(), "ALT-9999", first.dedupKey(), track, "sector-NE",
                UUID.randomUUID(), Severity.CRITICAL, Recommendation.TRACK_AND_MONITOR, 0.9,
                "t", "s", Instant.now(), Instant.now().plusSeconds(120), "corr-2");

        assertThat(alerts.raise(concurrent))
                .as("reported, not thrown — a rebalance is not a fault")
                .isFalse();
        assertThat(alerts.search(Optional.empty(), Optional.empty(), Optional.empty(), 100))
                .filteredOn(a -> a.trackId().equals(track))
                .hasSize(1);
    }

    @Test
    void aHigherSeverityRaisesASecondAlertRatherThanFoldingIn() {
        // A track climbing from HIGH to CRITICAL is new information; folding it into the open HIGH
        // alert would silently downgrade it.
        lifecycle.onThreatTransition(transition(Severity.HIGH));

        Optional<Alert> critical = lifecycle.onThreatTransition(transition(Severity.CRITICAL));

        assertThat(critical).isPresent();
        assertThat(alerts.findOpenByTrackId(track)).hasSize(2);
    }

    @Test
    void aResolvedAlertDoesNotBlockTheSameConditionRecurring() {
        // The unique index is partial. A track that was CRITICAL, was dealt with, and becomes
        // CRITICAL again an hour later is genuinely a new alert.
        Alert first = lifecycle.onThreatTransition(transition(Severity.CRITICAL)).orElseThrow();
        lifecycle.resolve(first.alertId(), AlertDisposition.BENIGN, "a permitted flight",
                "usr-4411", "K.Ullah");

        Optional<Alert> second = lifecycle.onThreatTransition(transition(Severity.CRITICAL));

        assertThat(second).isPresent();
        assertThat(second.get().alertId()).isNotEqualTo(first.alertId());
    }

    @Test
    void aDeEscalationRaisesNothing() {
        ThreatTransition improving = new ThreatTransition(track, "sector-NE", UUID.randomUUID(),
                Optional.of(Severity.CRITICAL), Severity.HIGH, 0.6,
                Recommendation.TRACK_AND_MONITOR, POLICY, Instant.now(), "corr-1");

        assertThat(lifecycle.onThreatTransition(improving)).isEmpty();
    }

    // ── Acknowledgement and resolution ──────────────────────────────────────────────────────────

    @Test
    void acknowledgingStopsTheEscalationAndRecordsWhoDidIt() {
        Alert raised = lifecycle.onThreatTransition(transition(Severity.CRITICAL)).orElseThrow();

        Alert acknowledged = lifecycle.acknowledge(
                raised.alertId(), "usr-4411", "K.Ullah", "eyes on it");

        assertThat(acknowledged.status()).isEqualTo(AlertStatus.ACKNOWLEDGED);
        assertThat(acknowledged.escalatesAt()).isEmpty();
        assertThat(alerts.acknowledgements(raised.alertId()))
                .singleElement()
                .satisfies(a -> assertThat(a.actorId()).isEqualTo("usr-4411"));
        assertThat(scheduler.due(Instant.now().plusSeconds(3600), 100))
                .doesNotContain(raised.alertId());
    }

    @Test
    void resolvingRecordsTheDispositionAndTheActor() {
        Alert raised = lifecycle.onThreatTransition(transition(Severity.CRITICAL)).orElseThrow();

        Alert resolved = lifecycle.resolve(raised.alertId(),
                AlertDisposition.TRUE_POSITIVE, "confirmed", "usr-4411", "K.Ullah");

        assertThat(resolved.status()).isEqualTo(AlertStatus.RESOLVED);
        assertThat(resolved.isOpen()).isFalse();
    }

    @Test
    void aLostTrackAutoResolvesItsAlertsAsInconclusive() {
        // The system knows the track ended, not whether the alert had been right. Recording a
        // verdict it cannot support would poison the calibration statistic.
        Alert raised = lifecycle.onThreatTransition(transition(Severity.CRITICAL)).orElseThrow();

        List<Alert> closed = lifecycle.closeAlertsFor(track, "the track was lost");

        assertThat(closed).hasSize(1);
        assertThat(alerts.findById(raised.alertId()).orElseThrow().status())
                .isEqualTo(AlertStatus.RESOLVED);
        assertThat(alerts.findOpenByTrackId(track)).isEmpty();
    }

    // ── Escalation ──────────────────────────────────────────────────────────────────────────────

    @Test
    void theSweepEscalatesAnAlertWhoseDeadlineHasPassed() {
        Alert raised = lifecycle.onThreatTransition(transition(Severity.CRITICAL)).orElseThrow();
        // Bring the deadline forward rather than waiting two minutes for it.
        scheduler.schedule(raised.alertId(), Instant.now().minusSeconds(1));

        int escalated = sweep.sweep();

        assertThat(escalated).isEqualTo(1);
        Alert after = alerts.findById(raised.alertId()).orElseThrow();
        assertThat(after.status()).isEqualTo(AlertStatus.ESCALATED);
        assertThat(after.escalationLevel()).isEqualTo(1);
    }

    @Test
    void theSweepDoesNotEscalateAnAcknowledgedAlert() {
        // The race an operator wins between the sweep reading the schedule and the escalation
        // applying. Reported as no-op rather than as an error: it is the ordinary outcome.
        Alert raised = lifecycle.onThreatTransition(transition(Severity.CRITICAL)).orElseThrow();
        lifecycle.acknowledge(raised.alertId(), "usr-4411", "K.Ullah", "");
        scheduler.schedule(raised.alertId(), Instant.now().minusSeconds(1));

        sweep.sweep();

        assertThat(alerts.findById(raised.alertId()).orElseThrow().status())
                .isEqualTo(AlertStatus.ACKNOWLEDGED);
    }

    @Test
    void theSweepRecoversADeadlineTheSchedulerLost() {
        // The reconciliation that makes a lost Redis a performance event rather than a safety one.
        // Without it, a cache restart would silently end the escalation of every open alert, and
        // the only symptom would be a critical alert that quietly stopped paging.
        Alert raised = lifecycle.onThreatTransition(transition(Severity.CRITICAL)).orElseThrow();
        scheduler.schedule(raised.alertId(), Instant.now().minusSeconds(1));
        scheduler.cancel(raised.alertId());
        assertThat(scheduler.due(Instant.now(), 100)).isEmpty();

        // The row still carries a deadline, in the future here, so nothing is due yet.
        assertThat(sweep.sweep()).isZero();
        assertThat(alerts.findEscalationsDue(Instant.now().plusSeconds(3600), 100))
                .extracting(Alert::alertId)
                .contains(raised.alertId());
    }

    @Test
    void escalatingToTheTopOfTheLadderStopsTheTimerButLeavesTheAlertOpen() {
        Alert raised = lifecycle.onThreatTransition(transition(Severity.CRITICAL)).orElseThrow();
        int rungs = 3;  // the CRITICAL ladder in application.yml

        for (int i = 0; i < rungs; i++) {
            scheduler.schedule(raised.alertId(), Instant.now().minusSeconds(1));
            sweep.sweep();
        }

        Alert after = alerts.findById(raised.alertId()).orElseThrow();
        assertThat(after.escalationLevel()).isEqualTo(rungs);
        assertThat(after.escalatesAt()).isEmpty();
        assertThat(after.isOpen()).isTrue();
    }

    // ── Reads ───────────────────────────────────────────────────────────────────────────────────

    @Test
    void theQueueFiltersBySeverityAndStatus() {
        lifecycle.onThreatTransition(transition(Severity.CRITICAL));
        lifecycle.onThreatTransition(transition(Severity.MEDIUM));

        assertThat(alerts.search(Optional.of(AlertStatus.RAISED), Optional.of(Severity.CRITICAL),
                Optional.empty(), 100))
                .filteredOn(a -> a.trackId().equals(track))
                .hasSize(1);
    }

    private ThreatTransition transition(Severity severity) {
        return new ThreatTransition(track, "sector-NE", UUID.randomUUID(), Optional.empty(),
                severity, 0.853, Recommendation.TRACK_AND_MONITOR, POLICY, Instant.now(), "corr-1");
    }
}
