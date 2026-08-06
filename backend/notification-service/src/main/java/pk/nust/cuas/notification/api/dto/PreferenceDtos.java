package pk.nust.cuas.notification.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;
import pk.nust.cuas.notification.domain.model.Channel;
import pk.nust.cuas.notification.domain.model.ChannelPreference;
import pk.nust.cuas.notification.domain.model.QuietHours;
import pk.nust.cuas.notification.domain.model.RecipientPreferences;
import pk.nust.cuas.notification.domain.model.Urgency;

/** Request and response DTOs for a user's notification preferences ({@code /notifications/preferences}). */
public final class PreferenceDtos {

    private PreferenceDtos() {
    }

    /**
     * The full replacement of a user's preferences. The channel list is the complete desired state:
     * a channel absent from it is removed, so a client reads, edits, and writes the whole set back.
     *
     * @param quietHoursStart when quiet hours begin, or null for none (both bounds or neither)
     * @param quietHoursEnd   when quiet hours end, or null for none
     * @param minSeverity     the lowest urgency worth notifying at
     * @param channels        the channels to configure
     */
    public record UpdatePreferencesRequest(
            @Schema(example = "22:00:00") LocalTime quietHoursStart,
            @Schema(example = "06:00:00") LocalTime quietHoursEnd,
            @NotNull Urgency minSeverity,
            @NotNull @Valid List<ChannelUpsert> channels) {

        /** @return the domain preferences for {@code userId}, validating the quiet-hours pairing */
        public RecipientPreferences toDomain(UUID userId) {
            return new RecipientPreferences(userId, quietHours(), minSeverity,
                    channels.stream()
                            .map(c -> new ChannelPreference(c.channel(), c.address(), c.enabled(),
                                    c.priority()))
                            .toList());
        }

        private QuietHours quietHours() {
            if (quietHoursStart == null && quietHoursEnd == null) {
                return QuietHours.none();
            }
            if (quietHoursStart == null || quietHoursEnd == null) {
                throw new IllegalArgumentException(
                        "quiet hours need both a start and an end, or neither");
            }
            return QuietHours.of(quietHoursStart, quietHoursEnd);
        }
    }

    /**
     * One channel in an update.
     *
     * @param channel  the channel
     * @param address  the delivery address (email, phone, webhook URL; the user id for websocket)
     * @param enabled  whether the recipient wants this channel used
     * @param priority preference order, lower first
     */
    public record ChannelUpsert(
            @NotNull Channel channel,
            @NotBlank String address,
            boolean enabled,
            @Min(0) @Max(1000) int priority) {
    }

    /**
     * A user's current preferences.
     *
     * @param quietHoursStart when quiet hours begin, or null for none
     * @param quietHoursEnd   when quiet hours end, or null for none
     * @param minSeverity     the lowest urgency worth notifying at
     * @param channels        the configured channels
     */
    public record PreferencesResponse(LocalTime quietHoursStart, LocalTime quietHoursEnd,
                                      Urgency minSeverity, List<ChannelView> channels) {

        public static PreferencesResponse from(RecipientPreferences prefs) {
            return new PreferencesResponse(prefs.quietHours().start().orElse(null),
                    prefs.quietHours().end().orElse(null), prefs.minSeverity(),
                    prefs.channels().stream()
                            .map(c -> new ChannelView(c.channel(), c.address(), c.enabled(), c.priority()))
                            .toList());
        }

        /** The shape returned when a user has never configured preferences. */
        public static PreferencesResponse defaults() {
            return new PreferencesResponse(null, null, Urgency.LOW, List.of());
        }
    }

    /**
     * One configured channel in a response.
     *
     * @param channel  the channel
     * @param address  the delivery address
     * @param enabled  whether it is in use
     * @param priority preference order, lower first
     */
    public record ChannelView(Channel channel, String address, boolean enabled, int priority) {
    }
}
