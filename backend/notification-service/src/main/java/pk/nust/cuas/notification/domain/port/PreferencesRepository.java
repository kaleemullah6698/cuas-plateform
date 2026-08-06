package pk.nust.cuas.notification.domain.port;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import pk.nust.cuas.notification.domain.model.RecipientPreferences;

/**
 * Persistence for per-user notification preferences and channels (ARCHITECTURE.md section 4.1.9,
 * {@code user_preferences} and {@code notification_channels}).
 */
public interface PreferencesRepository {

    /**
     * @param userId a user
     * @return the user's preferences, or empty if they have configured none
     */
    Optional<RecipientPreferences> find(UUID userId);

    /**
     * The recipients a source event fans out to: everyone with at least one enabled channel. The
     * dispatcher then filters each by minimum severity and quiet hours. Loading them per event is
     * acceptable at the operator-team scale this serves; a cache is a later refinement.
     *
     * @return the candidate recipients
     */
    List<RecipientPreferences> recipientsWithEnabledChannel();

    /** Upserts a user's preferences and channels. */
    void save(RecipientPreferences preferences);
}
