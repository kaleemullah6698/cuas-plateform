package pk.nust.cuas.notification.domain.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import pk.nust.cuas.notification.domain.model.Channel;
import pk.nust.cuas.notification.domain.model.ChannelPreference;
import pk.nust.cuas.notification.domain.model.QuietHours;
import pk.nust.cuas.notification.domain.model.RecipientPreferences;
import pk.nust.cuas.notification.domain.model.Urgency;

/** The two gates of {@link DeliverabilityPolicy}: the severity floor, then quiet hours. */
class DeliverabilityPolicyTest {

    private final DeliverabilityPolicy policy = new DeliverabilityPolicy();

    @Test
    void anUrgencyBelowTheFloorIsNotDeliveredAtAll() {
        RecipientPreferences prefs = preferences(QuietHours.none(), Urgency.HIGH);

        assertThat(policy.evaluate(prefs, Urgency.MEDIUM, LocalTime.NOON))
                .isEqualTo(DeliverabilityPolicy.Decision.BELOW_THRESHOLD);
    }

    @Test
    void aNonCriticalNotificationInQuietHoursIsHeld() {
        RecipientPreferences prefs = preferences(QuietHours.of(LocalTime.of(22, 0), LocalTime.of(6, 0)),
                Urgency.LOW);

        assertThat(policy.evaluate(prefs, Urgency.HIGH, LocalTime.of(23, 0)))
                .isEqualTo(DeliverabilityPolicy.Decision.QUIET_HOURS);
    }

    @Test
    void aCriticalNotificationIsNeverSilencedByQuietHours() {
        RecipientPreferences prefs = preferences(QuietHours.of(LocalTime.of(22, 0), LocalTime.of(6, 0)),
                Urgency.LOW);

        assertThat(policy.evaluate(prefs, Urgency.CRITICAL, LocalTime.of(23, 0)))
                .isEqualTo(DeliverabilityPolicy.Decision.DELIVER);
    }

    @Test
    void anAcceptedUrgencyOutsideQuietHoursIsDelivered() {
        RecipientPreferences prefs = preferences(QuietHours.of(LocalTime.of(22, 0), LocalTime.of(6, 0)),
                Urgency.LOW);

        assertThat(policy.evaluate(prefs, Urgency.MEDIUM, LocalTime.NOON))
                .isEqualTo(DeliverabilityPolicy.Decision.DELIVER);
    }

    private static RecipientPreferences preferences(QuietHours quietHours, Urgency minSeverity) {
        return new RecipientPreferences(UUID.randomUUID(), quietHours, minSeverity,
                List.of(new ChannelPreference(Channel.EMAIL, "ops@example.mil", true, 10)));
    }
}
