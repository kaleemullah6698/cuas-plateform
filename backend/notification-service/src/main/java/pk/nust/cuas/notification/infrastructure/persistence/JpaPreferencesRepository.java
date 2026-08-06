package pk.nust.cuas.notification.infrastructure.persistence;

import java.time.Clock;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import pk.nust.cuas.notification.domain.model.Channel;
import pk.nust.cuas.notification.domain.model.ChannelPreference;
import pk.nust.cuas.notification.domain.model.QuietHours;
import pk.nust.cuas.notification.domain.model.RecipientPreferences;
import pk.nust.cuas.notification.domain.model.Urgency;
import pk.nust.cuas.notification.domain.port.PreferencesRepository;
import pk.nust.cuas.notification.infrastructure.persistence.entity.NotificationChannelEntity;
import pk.nust.cuas.notification.infrastructure.persistence.entity.UserPreferencesEntity;
import pk.nust.cuas.notification.infrastructure.persistence.repository.NotificationChannelJpaRepository;
import pk.nust.cuas.notification.infrastructure.persistence.repository.UserPreferencesJpaRepository;

/** JPA implementation of {@link PreferencesRepository} over {@code notifydb}. */
@Repository
public class JpaPreferencesRepository implements PreferencesRepository {

    private final UserPreferencesJpaRepository preferences;
    private final NotificationChannelJpaRepository channels;
    private final Clock clock;

    public JpaPreferencesRepository(UserPreferencesJpaRepository preferences,
                                    NotificationChannelJpaRepository channels, Clock clock) {
        this.preferences = preferences;
        this.channels = channels;
        this.clock = clock;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<RecipientPreferences> find(UUID userId) {
        List<NotificationChannelEntity> channelRows = channels.findByUserId(userId);
        Optional<UserPreferencesEntity> prefs = preferences.findById(userId);
        if (channelRows.isEmpty() && prefs.isEmpty()) {
            return Optional.empty();
        }
        QuietHours quietHours = prefs.map(JpaPreferencesRepository::toQuietHours).orElseGet(QuietHours::none);
        Urgency minSeverity = prefs.map(p -> Urgency.valueOf(p.getMinSeverity())).orElse(Urgency.LOW);
        List<ChannelPreference> channelPreferences = channelRows.stream()
                .map(c -> new ChannelPreference(Channel.valueOf(c.getChannel()), c.getAddress(),
                        c.isEnabled(), c.getPriority()))
                .toList();
        return Optional.of(new RecipientPreferences(userId, quietHours, minSeverity, channelPreferences));
    }

    @Override
    @Transactional(readOnly = true)
    public List<RecipientPreferences> recipientsWithEnabledChannel() {
        return channels.findUserIdsWithEnabledChannel().stream()
                .map(this::find)
                .flatMap(Optional::stream)
                .toList();
    }

    @Override
    @Transactional
    public void save(RecipientPreferences prefs) {
        var start = prefs.quietHours().start().orElse(null);
        var end = prefs.quietHours().end().orElse(null);
        UserPreferencesEntity entity = preferences.findById(prefs.userId()).orElseGet(
                () -> new UserPreferencesEntity(prefs.userId(), start, end,
                        prefs.minSeverity().name(), clock.instant()));
        entity.apply(start, end, prefs.minSeverity().name(), clock.instant());
        preferences.save(entity);

        // Replace the channel set: the request is the complete desired state, so a channel absent
        // from it is removed. Delete then insert, in this transaction.
        channels.deleteByUserId(prefs.userId());
        channels.flush();
        prefs.channels().forEach(channel -> channels.save(new NotificationChannelEntity(
                prefs.userId(), channel.channel().name(), channel.address(), channel.enabled(),
                channel.priority(), clock.instant())));
    }

    private static QuietHours toQuietHours(UserPreferencesEntity entity) {
        if (entity.getQuietHoursStart() == null || entity.getQuietHoursEnd() == null) {
            return QuietHours.none();
        }
        return QuietHours.of(entity.getQuietHoursStart(), entity.getQuietHoursEnd());
    }
}
