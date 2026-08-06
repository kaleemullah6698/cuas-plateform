package pk.nust.cuas.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.time.Clock;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.List;
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
import pk.nust.cuas.notification.application.NotificationDispatchService;
import pk.nust.cuas.notification.application.NotificationRetryService;
import pk.nust.cuas.notification.application.port.NotificationEventPublisher;
import pk.nust.cuas.notification.domain.model.Channel;
import pk.nust.cuas.notification.domain.model.ChannelPreference;
import pk.nust.cuas.notification.domain.model.DeliveryFailureReason;
import pk.nust.cuas.notification.domain.model.Notification;
import pk.nust.cuas.notification.domain.model.NotificationStatus;
import pk.nust.cuas.notification.domain.model.NotificationTrigger;
import pk.nust.cuas.notification.domain.model.QuietHours;
import pk.nust.cuas.notification.domain.model.RecipientPreferences;
import pk.nust.cuas.notification.domain.model.SubjectRef;
import pk.nust.cuas.notification.domain.model.TriggerEvent;
import pk.nust.cuas.notification.domain.model.Urgency;
import pk.nust.cuas.notification.domain.port.NotificationRepository;
import pk.nust.cuas.notification.domain.port.PreferencesRepository;

/**
 * Delivery, deduplication, quiet-hours gating and DB-backed retry against a real PostgreSQL
 * (ARCHITECTURE.md section 4.1.9).
 *
 * <p>Against a real database rather than fakes, because the things that matter most are exactly what a
 * fake would get approximately right: that {@code uq_notification_dedup} rejects a concurrent
 * duplicate, that the Flyway migration applies and Hibernate validates against it, and that the retry
 * claim leases a due row under {@code SKIP LOCKED} so a second sweep skips it. Kafka is mocked — the
 * publisher is a port whose Kafka adapter has no logic worth a broker here; what is under test is
 * which outcomes the pipeline decides to record and emit.
 */
@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
class NotificationServiceIntegrationTest {

    @Container
    @SuppressWarnings("resource")
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine")
            .withDatabaseName("notifydb");

    @DynamicPropertySource
    static void infrastructure(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        // No broker in this suite; the listeners would otherwise retry a connection for its duration,
        // and the background retry sweep would re-deliver out from under an assertion.
        registry.add("spring.kafka.listener.auto-startup", () -> "false");
        registry.add("notification.retry.enabled", () -> "false");
    }

    @Autowired
    private NotificationDispatchService dispatch;

    @Autowired
    private NotificationRetryService retryService;

    @Autowired
    private PreferencesRepository preferences;

    @Autowired
    private NotificationRepository notifications;

    @Autowired
    private Clock clock;

    @Autowired
    private JdbcTemplate jdbc;

    @MockitoBean
    private NotificationEventPublisher events;

    private UUID user;

    @BeforeEach
    void freshState() {
        jdbc.execute("TRUNCATE notification_log, delivery_receipts, notification_channels, "
                + "user_preferences RESTART IDENTITY CASCADE");
        user = UUID.randomUUID();
    }

    // ── Preferences ───────────────────────────────────────────────────────────────────────────────

    @Test
    void preferencesRoundTripThroughTheDatabase() {
        preferences.save(new RecipientPreferences(user, QuietHours.of(LocalTime.of(22, 0),
                LocalTime.of(6, 0)), Urgency.MEDIUM,
                List.of(new ChannelPreference(Channel.EMAIL, "ops@example.mil", true, 10))));

        RecipientPreferences loaded = preferences.find(user).orElseThrow();
        assertThat(loaded.minSeverity()).isEqualTo(Urgency.MEDIUM);
        assertThat(loaded.quietHours().start()).contains(LocalTime.of(22, 0));
        assertThat(loaded.channels()).singleElement()
                .satisfies(c -> assertThat(c.address()).isEqualTo("ops@example.mil"));
        assertThat(preferences.recipientsWithEnabledChannel())
                .extracting(RecipientPreferences::userId).contains(user);
    }

    @Test
    void savingPreferencesReplacesTheWholeChannelSet() {
        preferences.save(prefs(Urgency.LOW, QuietHours.none(),
                new ChannelPreference(Channel.EMAIL, "ops@example.mil", true, 10),
                new ChannelPreference(Channel.SMS, "+100", true, 20)));

        preferences.save(prefs(Urgency.LOW, QuietHours.none(),
                new ChannelPreference(Channel.WEBHOOK, "https://hook", true, 5)));

        assertThat(preferences.find(user).orElseThrow().channels())
                .singleElement().satisfies(c -> assertThat(c.channel()).isEqualTo(Channel.WEBHOOK));
    }

    // ── Dispatch ──────────────────────────────────────────────────────────────────────────────────

    @Test
    void anEventIsDeliveredOnTheRecipientsPreferredChannel() {
        preferences.save(prefs(Urgency.LOW, QuietHours.none(),
                new ChannelPreference(Channel.SMS, "+100", true, 20),
                new ChannelPreference(Channel.EMAIL, "ops@example.mil", true, 10)));

        dispatch.dispatch(event(Urgency.HIGH, "dedup-1"));

        assertThat(notifications.history(user, 10)).singleElement().satisfies(n -> {
            assertThat(n.status()).isEqualTo(NotificationStatus.SENT);
            assertThat(n.channel()).isEqualTo(Channel.EMAIL);
        });
        verify(events).publishSent(org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.anyLong());
    }

    @Test
    void aRecurringEventNotifiesTheRecipientOnlyOnce() {
        preferences.save(prefs(Urgency.LOW, QuietHours.none(),
                new ChannelPreference(Channel.EMAIL, "ops@example.mil", true, 10)));

        dispatch.dispatch(event(Urgency.HIGH, "dedup-1"));
        dispatch.dispatch(event(Urgency.HIGH, "dedup-1"));

        assertThat(notifications.history(user, 10)).hasSize(1);
    }

    @Test
    void quietHoursHoldANonCriticalEventWithoutDelivering() {
        preferences.save(prefs(Urgency.LOW, windowAroundNow(),
                new ChannelPreference(Channel.EMAIL, "ops@example.mil", true, 10)));

        dispatch.dispatch(event(Urgency.HIGH, "dedup-1"));

        assertThat(notifications.history(user, 10)).singleElement()
                .satisfies(n -> assertThat(n.status()).isEqualTo(NotificationStatus.SUPPRESSED));
        verify(events, never()).publishSent(org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.anyLong());
    }

    @Test
    void aCriticalEventIsDeliveredEvenInQuietHours() {
        preferences.save(prefs(Urgency.LOW, windowAroundNow(),
                new ChannelPreference(Channel.EMAIL, "ops@example.mil", true, 10)));

        dispatch.dispatch(event(Urgency.CRITICAL, "dedup-1"));

        assertThat(notifications.history(user, 10)).singleElement()
                .satisfies(n -> assertThat(n.status()).isEqualTo(NotificationStatus.SENT));
    }

    @Test
    void anEventBelowTheRecipientsFloorCreatesNoNotification() {
        preferences.save(prefs(Urgency.CRITICAL, QuietHours.none(),
                new ChannelPreference(Channel.EMAIL, "ops@example.mil", true, 10)));

        dispatch.dispatch(event(Urgency.MEDIUM, "dedup-1"));

        assertThat(notifications.history(user, 10)).isEmpty();
    }

    // ── Retry ─────────────────────────────────────────────────────────────────────────────────────

    @Test
    void aDueRetryIsClaimedAndThenLeasedOutOfTheWayOfASecondSweep() {
        UUID id = UUID.randomUUID();
        Notification pending = open(id);
        notifications.create(pending);
        notifications.update(pending.failedRetryable(DeliveryFailureReason.PROVIDER_TIMEOUT,
                clock.instant(), clock.instant().minusSeconds(5)));

        List<Notification> firstClaim = notifications.claimRetryable(clock.instant(), 10);
        assertThat(firstClaim).extracting(Notification::notificationId).contains(id);

        // Leased into the future, so an immediate second sweep finds nothing due.
        assertThat(notifications.claimRetryable(clock.instant(), 10)).isEmpty();
    }

    @Test
    void aRetryWhoseChannelIsNoLongerConfiguredFailsTerminally() {
        // No preferences saved for this user, so the sweep finds no address to retry against.
        UUID id = UUID.randomUUID();
        Notification pending = open(id);
        notifications.create(pending);
        notifications.update(pending.failedRetryable(DeliveryFailureReason.PROVIDER_TIMEOUT,
                clock.instant(), clock.instant().minusSeconds(5)));

        retryService.sweep();

        Notification after = notifications.findById(id).orElseThrow();
        assertThat(after.status()).isEqualTo(NotificationStatus.FAILED);
        assertThat(after.reason()).contains(DeliveryFailureReason.CHANNEL_DISABLED);
    }

    // ── Test send ─────────────────────────────────────────────────────────────────────────────────

    @Test
    void aTestSendBypassesQuietHoursAndTheSeverityFloor() {
        preferences.save(prefs(Urgency.CRITICAL, windowAroundNow(),
                new ChannelPreference(Channel.EMAIL, "ops@example.mil", true, 10)));

        Optional<Notification> sent = dispatch.sendTest(user, "corr-test");

        assertThat(sent).isPresent();
        assertThat(sent.get().status()).isEqualTo(NotificationStatus.SENT);
        assertThat(sent.get().trigger()).isEqualTo(NotificationTrigger.TEST);
    }

    @Test
    void aTestSendWithNoEnabledChannelDeliversNothing() {
        preferences.save(prefs(Urgency.LOW, QuietHours.none(),
                new ChannelPreference(Channel.EMAIL, "ops@example.mil", false, 10)));

        assertThat(dispatch.sendTest(user, "corr-test")).isEmpty();
    }

    private RecipientPreferences prefs(Urgency minSeverity, QuietHours quietHours,
                                       ChannelPreference... channels) {
        return new RecipientPreferences(user, quietHours, minSeverity, List.of(channels));
    }

    private TriggerEvent event(Urgency urgency, String dedupSeed) {
        return new TriggerEvent(NotificationTrigger.ALERT_RAISED, urgency, dedupSeed,
                SubjectRef.ofAlert(UUID.randomUUID(), UUID.randomUUID()), "subject", "corr-1");
    }

    private Notification open(UUID id) {
        return Notification.open(id, user, Channel.EMAIL, NotificationTrigger.ALERT_RAISED,
                "dedup-" + id, Urgency.HIGH, SubjectRef.none(), "subject", "corr-1", clock.instant());
    }

    /** A quiet-hours window that certainly contains the current wall-clock time. */
    private static QuietHours windowAroundNow() {
        LocalTime now = LocalTime.now(ZoneOffset.UTC);
        return QuietHours.of(now.minusHours(1), now.plusHours(1));
    }
}
