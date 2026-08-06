package pk.nust.cuas.notification.application;

import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import pk.nust.cuas.notification.domain.model.RecipientPreferences;
import pk.nust.cuas.notification.domain.port.PreferencesRepository;

/**
 * Reads and writes a user's notification preferences (ARCHITECTURE.md section 4.1.9,
 * {@code GET/PUT /notifications/preferences}).
 */
@Service
public class PreferencesService {

    private final PreferencesRepository preferences;

    public PreferencesService(PreferencesRepository preferences) {
        this.preferences = preferences;
    }

    public Optional<RecipientPreferences> get(UUID userId) {
        return preferences.find(userId);
    }

    public void save(RecipientPreferences updated) {
        preferences.save(updated);
    }
}
