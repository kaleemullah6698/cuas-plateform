package pk.nust.cuas.notification.domain.model;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * A recipient's full notification preferences (section 4.1.9): their quiet hours, the minimum urgency
 * worth notifying them at, and the channels they have configured.
 *
 * @param userId       the recipient
 * @param quietHours   when non-critical notifications are held
 * @param minSeverity  the lowest urgency that reaches this recipient at all
 * @param channels     the configured channels
 */
public record RecipientPreferences(UUID userId, QuietHours quietHours, Urgency minSeverity,
                                   List<ChannelPreference> channels) {

    public RecipientPreferences {
        if (userId == null || quietHours == null || minSeverity == null || channels == null) {
            throw new IllegalArgumentException("all fields are required");
        }
        channels = List.copyOf(channels);
    }

    /** @return whether an event of this urgency is worth notifying this recipient about */
    public boolean accepts(Urgency urgency) {
        return urgency.atLeast(minSeverity);
    }

    /**
     * The single channel to use for one event — the enabled channel with the smallest priority. This
     * is the cross-channel choice: a recipient with several channels enabled is paged on their best
     * one, not on all of them.
     *
     * @return the preferred enabled channel, or empty if none is enabled
     */
    public Optional<ChannelPreference> preferredChannel() {
        return channels.stream()
                .filter(ChannelPreference::enabled)
                .min(Comparator.comparingInt(ChannelPreference::priority));
    }
}
