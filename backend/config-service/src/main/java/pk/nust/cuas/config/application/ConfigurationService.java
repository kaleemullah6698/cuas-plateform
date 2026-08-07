package pk.nust.cuas.config.application;

import java.time.Clock;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import pk.nust.cuas.config.domain.model.ConfigChange;
import pk.nust.cuas.config.domain.model.ConfigNamespaceKind;
import pk.nust.cuas.config.domain.model.ConfigVersion;
import pk.nust.cuas.config.domain.model.Configuration;
import pk.nust.cuas.config.domain.port.ConfigChangeNotifier;
import pk.nust.cuas.config.domain.port.ConfigEventPublisher;
import pk.nust.cuas.config.domain.port.ConfigurationRepository;

/**
 * Reads and changes configuration namespaces (ARCHITECTURE.md §4.1.12).
 *
 * <p>A change is: validate and canonicalise the value; compute the new version from the current one
 * (or open the namespace at version 1); persist the new state, its history snapshot and the change-log
 * entry <em>atomically</em>, so a change that cannot be audited does not happen (§7.5); then announce
 * it — on the Kafka mirror and to the live WatchConfig subscribers. Persistence commits before the
 * announcement, so a subscriber never hears of a change that did not durably take.
 */
@Service
public class ConfigurationService {

    private static final Logger log = LoggerFactory.getLogger(ConfigurationService.class);

    private final ConfigurationRepository repository;
    private final ConfigEventPublisher publisher;
    private final ConfigChangeNotifier notifier;
    private final ConfigCanonicalizer canonicalizer;
    private final Clock clock;

    public ConfigurationService(ConfigurationRepository repository, ConfigEventPublisher publisher,
                                ConfigChangeNotifier notifier, ConfigCanonicalizer canonicalizer,
                                Clock clock) {
        this.repository = repository;
        this.publisher = publisher;
        this.notifier = notifier;
        this.canonicalizer = canonicalizer;
        this.clock = clock;
    }

    public Optional<Configuration> get(String namespace) {
        return repository.find(namespace);
    }

    public List<ConfigVersion> history(String namespace, int limit) {
        if (repository.find(namespace).isEmpty()) {
            throw new ConfigNotFoundException("no configuration namespace '" + namespace + "'");
        }
        return repository.history(namespace, limit);
    }

    /** Every namespace's current state, optionally narrowed to a set of kinds — the WatchConfig snapshot. */
    public List<Configuration> snapshot(Set<ConfigNamespaceKind> kinds) {
        return kinds.isEmpty() ? repository.findAll() : repository.findByKinds(kinds);
    }

    /**
     * Sets a namespace's value, opening it if new and advancing its version if it exists.
     *
     * @throws IllegalArgumentException if the value is not a JSON object, or the kind contradicts the
     *                                  namespace's existing kind (a namespace's kind is fixed at creation)
     */
    public Configuration update(String namespace, ConfigNamespaceKind kind, String rawValueJson,
                                String actorId, String justification) {
        String canonical = canonicalizer.canonicalize(rawValueJson);
        Optional<Configuration> before = repository.find(namespace);
        before.ifPresent(existing -> requireSameKind(existing, kind));

        Configuration current = before
                .map(existing -> existing.updatedWith(canonical, actorId, justification, clock.instant()))
                .orElseGet(() -> Configuration.initial(namespace, kind, canonical, actorId,
                        justification, clock.instant()));

        persist(before, current, actorId, justification);
        log.info("Configuration '{}' set to version {} by {}", namespace, current.version(), actorId);
        return current;
    }

    /**
     * Restores a namespace to the value it held at an earlier version, as a new higher version.
     *
     * @throws ConfigNotFoundException if the namespace or the target version does not exist
     */
    public Configuration rollback(String namespace, long toVersion, String actorId,
                                  String justification) {
        Configuration current = repository.find(namespace).orElseThrow(
                () -> new ConfigNotFoundException("no configuration namespace '" + namespace + "'"));
        ConfigVersion target = repository.versionAt(namespace, toVersion).orElseThrow(
                () -> new ConfigNotFoundException(
                        "namespace '" + namespace + "' has no version " + toVersion));

        Configuration rolled = current.restoredFrom(target, actorId, justification, clock.instant());
        persist(Optional.of(current), rolled, actorId, justification);
        log.info("Configuration '{}' rolled back to the value of version {} (now version {}) by {}",
                namespace, toVersion, rolled.version(), actorId);
        return rolled;
    }

    private void persist(Optional<Configuration> before, Configuration current, String actorId,
                         String justification) {
        ConfigVersion snapshot = ConfigVersion.of(current);
        ConfigChange change = ConfigChange.between(before, current, actorId, justification);
        repository.persist(current, snapshot, change);
        publisher.publish(current);
        notifier.notifyChanged(current);
    }

    private static void requireSameKind(Configuration existing, ConfigNamespaceKind kind) {
        if (existing.kind() != kind) {
            throw new IllegalArgumentException("namespace '" + existing.namespace() + "' is kind "
                    + existing.kind() + "; it cannot be changed to " + kind);
        }
    }
}
