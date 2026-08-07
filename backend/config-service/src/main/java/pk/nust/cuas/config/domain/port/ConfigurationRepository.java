package pk.nust.cuas.config.domain.port;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import pk.nust.cuas.config.domain.model.ConfigChange;
import pk.nust.cuas.config.domain.model.ConfigNamespaceKind;
import pk.nust.cuas.config.domain.model.ConfigVersion;
import pk.nust.cuas.config.domain.model.Configuration;

/**
 * Persistence for configurations, their version history and their change log (ARCHITECTURE.md
 * §4.1.12). The interface is declared here in the domain; the JPA adapter is in
 * {@code infrastructure/persistence}.
 */
public interface ConfigurationRepository {

    /** The current state of a namespace, if it exists. */
    Optional<Configuration> find(String namespace);

    /** Every namespace's current state — the WatchConfig full-snapshot for an unfiltered subscriber. */
    List<Configuration> findAll();

    /** The current state of every namespace of one of the given kinds — a kind-filtered watch. */
    List<Configuration> findByKinds(Set<ConfigNamespaceKind> kinds);

    /** A namespace's version history, newest first, capped at {@code limit}. */
    List<ConfigVersion> history(String namespace, int limit);

    /** A specific past version of a namespace, for point-in-time recall and rollback. */
    Optional<ConfigVersion> versionAt(String namespace, long version);

    /**
     * Persists a change atomically: the new current state, its history snapshot, and the change-log
     * entry, in one transaction — so a change that cannot be fully recorded does not happen.
     *
     * @param current  the new current state
     * @param snapshot the history entry for it
     * @param change   the change-log entry
     */
    void persist(Configuration current, ConfigVersion snapshot, ConfigChange change);
}
