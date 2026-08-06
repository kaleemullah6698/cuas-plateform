package pk.nust.cuas.alert.domain.policy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import pk.nust.cuas.alert.domain.model.Alert;
import pk.nust.cuas.alert.domain.model.DedupKey;
import pk.nust.cuas.alert.domain.model.Recommendation;
import pk.nust.cuas.alert.domain.model.Severity;
import pk.nust.cuas.alert.domain.service.AlertRaisingPolicy;
import pk.nust.cuas.alert.domain.service.ThreatTransition;

/** The three policies that decide what interrupts an operator (ARCHITECTURE.md section 4.1.8). */
class AlertPolicyTest {

    private static final Instant T = Instant.parse("2026-08-06T14:23:06Z");
    private static final UUID TRACK = UUID.fromString("018f2b3c-4d5e-7f80-9a1b-2c3d4e5f6071");

    // ── Raising ─────────────────────────────────────────────────────────────────────────────────

    @Test
    void aDeEscalationRaisesNothing() {
        // A track dropping from CRITICAL to HIGH is good news, and good news does not belong in a
        // queue an operator works through. The open CRITICAL alert still needs resolving.
        ThreatTransition improving = transition(
                Optional.of(Severity.CRITICAL), Severity.HIGH);

        assertThat(AlertRaisingPolicy.DEFAULT.shouldRaise(improving)).isFalse();
    }

    @Test
    void aLowTrackDoesNotInterruptAnybody() {
        // LOW is where a bird and a distant permitted flight live. A queue that fills with them is
        // one nobody reads carefully by the time something matters.
        assertThat(AlertRaisingPolicy.DEFAULT.shouldRaise(
                transition(Optional.empty(), Severity.LOW))).isFalse();
    }

    @Test
    void aFirstAssessedLevelAtOrAboveTheThresholdRaises() {
        assertThat(AlertRaisingPolicy.DEFAULT.shouldRaise(
                transition(Optional.empty(), Severity.MEDIUM))).isTrue();
        assertThat(AlertRaisingPolicy.DEFAULT.shouldRaise(
                transition(Optional.of(Severity.MEDIUM), Severity.CRITICAL))).isTrue();
    }

    // ── Suppression ─────────────────────────────────────────────────────────────────────────────

    @Test
    void theWindowIsShorterAtHigherSeverity() {
        // One window for everything is wrong in both directions at once: long enough to tame a
        // flapping LOW track, it would silence a re-triggering CRITICAL one.
        SuppressionPolicy policy = SuppressionPolicy.DEFAULT;

        assertThat(policy.windowFor(Severity.CRITICAL))
                .isLessThan(policy.windowFor(Severity.HIGH));
        assertThat(policy.windowFor(Severity.HIGH))
                .isLessThan(policy.windowFor(Severity.MEDIUM));
        assertThat(policy.windowFor(Severity.MEDIUM))
                .isLessThan(policy.windowFor(Severity.LOW));
    }

    @Test
    void aRecurrenceInsideTheWindowIsSuppressed() {
        Alert open = raised(Severity.CRITICAL);

        assertThat(SuppressionPolicy.DEFAULT.suppresses(open, T.plusSeconds(60))).isTrue();
    }

    @Test
    void aRecurrenceAfterTheWindowRaisesAgain() {
        Alert open = raised(Severity.CRITICAL);

        assertThat(SuppressionPolicy.DEFAULT.suppresses(open, T.plusSeconds(200))).isFalse();
    }

    @Test
    void theWindowIsMeasuredFromTheRaiseNotFromTheLastRecurrence() {
        // A window that reset on every recurrence would never expire for a persistently flapping
        // track, so one alert could suppress its own successors indefinitely.
        Alert open = raised(Severity.CRITICAL);
        open.recordRecurrence();
        open.recordRecurrence();

        assertThat(SuppressionPolicy.DEFAULT.suppresses(open, T.plusSeconds(200))).isFalse();
    }

    @Test
    void aResolvedAlertSuppressesNothing() {
        Alert resolved = raised(Severity.CRITICAL);
        resolved.resolve(T.plusSeconds(30));

        assertThat(SuppressionPolicy.DEFAULT.suppresses(resolved, T.plusSeconds(40))).isFalse();
    }

    @Test
    void aSeverityWithNoWindowIsRejected() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new SuppressionPolicy(Map.of(Severity.LOW, Duration.ofMinutes(5))))
                .withMessageContaining("No suppression window configured");
    }

    // ── Escalation ──────────────────────────────────────────────────────────────────────────────

    @Test
    void theLadderIsFinite() {
        EscalationPolicy policy = EscalationPolicy.DEFAULT;
        int top = policy.maxLevel(Severity.CRITICAL);

        assertThat(policy.nextEscalationAt(Severity.CRITICAL, top, T))
                .as("past the top rung an alert stays open but stops paging")
                .isEmpty();
    }

    @Test
    void theFirstRungIsShorterAtHigherSeverity() {
        EscalationPolicy policy = EscalationPolicy.DEFAULT;

        Instant critical = policy.nextEscalationAt(Severity.CRITICAL, 0, T).orElseThrow();
        Instant low = policy.nextEscalationAt(Severity.LOW, 0, T).orElseThrow();

        assertThat(critical).isBefore(low);
    }

    @Test
    void rungsAreMeasuredFromTheInstantGiven() {
        // From "now", not from the raise: each rung is a fresh window in which somebody could still
        // acknowledge, and measuring from the raise would collapse the later rungs together.
        Instant second = EscalationPolicy.DEFAULT
                .nextEscalationAt(Severity.CRITICAL, 1, T.plusSeconds(120)).orElseThrow();

        assertThat(second).isEqualTo(T.plusSeconds(120).plus(Duration.ofMinutes(5)));
    }

    @Test
    void aSeverityWithNoLadderIsRejected() {
        // The failure mode of "no ladder" is an alert that never escalates — silent, and only
        // discovered when somebody asks why nobody was paged.
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new EscalationPolicy(
                        Map.of(Severity.LOW, List.of(Duration.ofMinutes(5)))))
                .withMessageContaining("No escalation ladder configured");
    }

    @Test
    void aZeroLengthRungIsRejected() {
        assertThatIllegalArgumentException().isThrownBy(() -> new EscalationPolicy(Map.of(
                Severity.LOW, List.of(Duration.ZERO),
                Severity.MEDIUM, List.of(Duration.ofMinutes(5)),
                Severity.HIGH, List.of(Duration.ofMinutes(5)),
                Severity.CRITICAL, List.of(Duration.ofMinutes(5)))));
    }

    private static ThreatTransition transition(Optional<Severity> previous, Severity current) {
        return new ThreatTransition(TRACK, "sector-NE", UUID.randomUUID(), previous, current,
                0.62, Recommendation.TRACK_AND_MONITOR, "policy-v3", T, "corr-1");
    }

    private static Alert raised(Severity severity) {
        return Alert.raise(UUID.randomUUID(), "ALT-0001",
                new DedupKey(TRACK, severity, "policy-v3"), TRACK, "sector-NE", UUID.randomUUID(),
                severity, Recommendation.TRACK_AND_MONITOR, 0.853, "t", "s", T,
                T.plusSeconds(120), "corr-1");
    }
}
