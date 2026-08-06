package pk.nust.cuas.notification.domain.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** The cross-channel choice and severity acceptance of {@link RecipientPreferences}. */
class RecipientPreferencesTest {

    @Test
    void thePreferredChannelIsTheEnabledOneWithTheSmallestPriority() {
        RecipientPreferences prefs = new RecipientPreferences(UUID.randomUUID(), QuietHours.none(),
                Urgency.LOW, List.of(
                        new ChannelPreference(Channel.SMS, "+100", true, 20),
                        new ChannelPreference(Channel.EMAIL, "ops@example.mil", true, 10),
                        new ChannelPreference(Channel.WEBHOOK, "https://hook", true, 5)));

        assertThat(prefs.preferredChannel()).map(ChannelPreference::channel).contains(Channel.WEBHOOK);
    }

    @Test
    void aDisabledChannelIsNeverPreferredHoweverLowItsPriority() {
        RecipientPreferences prefs = new RecipientPreferences(UUID.randomUUID(), QuietHours.none(),
                Urgency.LOW, List.of(
                        new ChannelPreference(Channel.WEBHOOK, "https://hook", false, 1),
                        new ChannelPreference(Channel.EMAIL, "ops@example.mil", true, 10)));

        assertThat(prefs.preferredChannel()).map(ChannelPreference::channel).contains(Channel.EMAIL);
    }

    @Test
    void noEnabledChannelMeansNoPreferredChannel() {
        RecipientPreferences prefs = new RecipientPreferences(UUID.randomUUID(), QuietHours.none(),
                Urgency.LOW, List.of(new ChannelPreference(Channel.EMAIL, "ops@example.mil", false, 10)));

        assertThat(prefs.preferredChannel()).isEmpty();
    }

    @Test
    void severityAcceptanceIsAtOrAboveTheFloor() {
        RecipientPreferences prefs = new RecipientPreferences(UUID.randomUUID(), QuietHours.none(),
                Urgency.HIGH, List.of());

        assertThat(prefs.accepts(Urgency.CRITICAL)).isTrue();
        assertThat(prefs.accepts(Urgency.HIGH)).isTrue();
        assertThat(prefs.accepts(Urgency.MEDIUM)).isFalse();
    }
}
