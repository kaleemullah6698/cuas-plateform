package pk.nust.cuas.alert.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * The alert aggregate's transitions.
 *
 * <p>The rules live on the aggregate rather than in a service, so an illegal transition is
 * impossible rather than merely unlikely. These tests pin the ones that would otherwise be easy to
 * break: what stops an escalation timer, what does not, and what a terminal alert refuses.
 */
class AlertTest {

    private static final Instant T = Instant.parse("2026-08-06T14:23:06Z");
    private static final UUID TRACK = UUID.fromString("018f2b3c-4d5e-7f80-9a1b-2c3d4e5f6071");

    @Test
    void aRaisedAlertCarriesItsFirstEscalationDeadline() {
        Alert alert = raised();

        assertThat(alert.status()).isEqualTo(AlertStatus.RAISED);
        assertThat(alert.escalatesAt()).contains(T.plusSeconds(120));
        assertThat(alert.occurrenceCount()).isEqualTo(1);
        assertThat(alert.isOpen()).isTrue();
    }

    @Test
    void acknowledgingClearsTheDeadlineRatherThanIgnoringIt() {
        // A stale escalatesAt on an acknowledged alert would fire the moment anything rebuilt the
        // schedule from the database — which the sweep does on every pass.
        Alert alert = raised();

        alert.acknowledge(T.plusSeconds(30));

        assertThat(alert.status()).isEqualTo(AlertStatus.ACKNOWLEDGED);
        assertThat(alert.acknowledgedAt()).contains(T.plusSeconds(30));
        assertThat(alert.escalatesAt()).isEmpty();
    }

    @Test
    void acknowledgingTwiceIsIdempotent() {
        // Two operators can click at once; failing the second would report a problem where there is
        // none, and the console would show an error for a successful action.
        Alert alert = raised();
        alert.acknowledge(T.plusSeconds(30));

        alert.acknowledge(T.plusSeconds(45));

        assertThat(alert.acknowledgedAt())
                .as("the first acknowledgement is the one that counts")
                .contains(T.plusSeconds(30));
    }

    @Test
    void assigningDoesNotStopTheEscalationTimer() {
        // Assignment says "this is yours"; acknowledgement says "I have seen it". Handing work to
        // somebody who has not read it is not attending to it.
        Alert alert = raised();

        alert.assignTo("usr-2210");

        assertThat(alert.assignedTo()).contains("usr-2210");
        assertThat(alert.escalatesAt()).contains(T.plusSeconds(120));
        assertThat(alert.status()).isEqualTo(AlertStatus.RAISED);
    }

    @Test
    void aRecurrenceRaisesTheCountAndNothingElse() {
        Alert alert = raised();

        alert.recordRecurrence();
        alert.recordRecurrence();

        assertThat(alert.occurrenceCount()).isEqualTo(3);
        assertThat(alert.status()).isEqualTo(AlertStatus.RAISED);
    }

    @Test
    void aRecurrenceDoesNotPostponeTheEscalation() {
        // Otherwise the most persistent alert would be the one that never reaches a supervisor.
        Alert alert = raised();

        alert.recordRecurrence();

        assertThat(alert.escalatesAt()).contains(T.plusSeconds(120));
    }

    @Test
    void escalatingAdvancesTheLevelAndTheDeadline() {
        Alert alert = raised();

        alert.escalate(T.plusSeconds(120), T.plusSeconds(420));

        assertThat(alert.status()).isEqualTo(AlertStatus.ESCALATED);
        assertThat(alert.escalationLevel()).isEqualTo(1);
        assertThat(alert.escalatesAt()).contains(T.plusSeconds(420));
    }

    @Test
    void theTopOfTheLadderLeavesTheAlertOpenButQuiet() {
        // An alert that escalated forever would train operators to mute the channel, after which
        // the next genuine escalation reaches nobody.
        Alert alert = raised();
        alert.escalate(T.plusSeconds(120), null);

        assertThat(alert.escalatesAt()).isEmpty();
        assertThat(alert.isOpen()).isTrue();
    }

    @Test
    void anAcknowledgedAlertCannotEscalate() {
        Alert alert = raised();
        alert.acknowledge(T.plusSeconds(30));

        assertThatIllegalStateException()
                .isThrownBy(() -> alert.escalate(T.plusSeconds(120), null))
                .withMessageContaining("ACKNOWLEDGED");
    }

    @Test
    void resolvingIsTerminalAndClearsTheDeadline() {
        Alert alert = raised();

        alert.resolve(T.plusSeconds(300));

        assertThat(alert.status()).isEqualTo(AlertStatus.RESOLVED);
        assertThat(alert.resolvedAt()).contains(T.plusSeconds(300));
        assertThat(alert.escalatesAt()).isEmpty();
        assertThat(alert.isOpen()).isFalse();
    }

    @Test
    void aResolvedAlertRefusesEveryFurtherTransition() {
        Alert alert = raised();
        alert.resolve(T.plusSeconds(300));

        assertThatIllegalStateException().isThrownBy(() -> alert.acknowledge(T.plusSeconds(400)));
        assertThatIllegalStateException().isThrownBy(() -> alert.resolve(T.plusSeconds(400)));
        assertThatIllegalStateException().isThrownBy(() -> alert.recordRecurrence());
        assertThatIllegalStateException().isThrownBy(() -> alert.assignTo("usr-1"));
    }

    @Test
    void theDedupKeyIsTrackSeverityAndPolicy() {
        DedupKey key = new DedupKey(TRACK, Severity.CRITICAL, "policy-v3");

        assertThat(key.value()).isEqualTo(TRACK + "|CRITICAL|policy-v3");
    }

    @Test
    void aSeverityChangeIsADifferentDedupKey() {
        // A track climbing from HIGH to CRITICAL is new information an operator must see; folding it
        // into the open HIGH alert would silently downgrade it.
        assertThat(new DedupKey(TRACK, Severity.HIGH, "policy-v3").value())
                .isNotEqualTo(new DedupKey(TRACK, Severity.CRITICAL, "policy-v3").value());
    }

    @Test
    void aPolicyChangeIsADifferentDedupKey() {
        // Without this, a retune would be invisible to anyone watching the queue: the open alert
        // would persist, apparently unchanged, under rules that no longer applied.
        assertThat(new DedupKey(TRACK, Severity.HIGH, "policy-v3").value())
                .isNotEqualTo(new DedupKey(TRACK, Severity.HIGH, "policy-v4").value());
    }

    private static Alert raised() {
        return Alert.raise(
                UUID.randomUUID(), "ALT-0001",
                new DedupKey(TRACK, Severity.CRITICAL, "policy-v3"), TRACK, "sector-NE",
                UUID.randomUUID(), Severity.CRITICAL, Recommendation.TRACK_AND_MONITOR, 0.853,
                "CRITICAL track", "closing on SITE-1", T, T.plusSeconds(120), "corr-1");
    }
}
