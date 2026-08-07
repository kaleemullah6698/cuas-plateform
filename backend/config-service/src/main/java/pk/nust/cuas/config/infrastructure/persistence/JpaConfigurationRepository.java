package pk.nust.cuas.config.infrastructure.persistence;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import pk.nust.cuas.config.domain.model.ConfigChange;
import pk.nust.cuas.config.domain.model.ConfigNamespaceKind;
import pk.nust.cuas.config.domain.model.ConfigVersion;
import pk.nust.cuas.config.domain.model.Configuration;
import pk.nust.cuas.config.domain.port.ConfigurationRepository;
import pk.nust.cuas.config.infrastructure.persistence.entity.ConfigChangeEntity;
import pk.nust.cuas.config.infrastructure.persistence.entity.ConfigVersionEntity;
import pk.nust.cuas.config.infrastructure.persistence.entity.ConfigurationEntity;
import pk.nust.cuas.config.infrastructure.persistence.repository.ConfigChangeJpaRepository;
import pk.nust.cuas.config.infrastructure.persistence.repository.ConfigVersionJpaRepository;
import pk.nust.cuas.config.infrastructure.persistence.repository.ConfigurationJpaRepository;

/**
 * JPA implementation of {@link ConfigurationRepository} over {@code configdb}.
 *
 * <p>The three writes of a change — the current state, its history snapshot, and the change-log entry
 * — happen in one transaction, so the audit record and the change it describes commit together or not
 * at all (§7.5). Optimistic locking on the current row (its {@code @Version}) turns two concurrent
 * edits of one namespace into a lost-update failure for the loser rather than a silently clobbered
 * change.
 */
@Repository
public class JpaConfigurationRepository implements ConfigurationRepository {

    private final ConfigurationJpaRepository configurations;
    private final ConfigVersionJpaRepository versions;
    private final ConfigChangeJpaRepository changes;

    public JpaConfigurationRepository(ConfigurationJpaRepository configurations,
                                      ConfigVersionJpaRepository versions,
                                      ConfigChangeJpaRepository changes) {
        this.configurations = configurations;
        this.versions = versions;
        this.changes = changes;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Configuration> find(String namespace) {
        return configurations.findById(namespace).map(JpaConfigurationRepository::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Configuration> findAll() {
        return configurations.findAll().stream().map(JpaConfigurationRepository::toDomain).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<Configuration> findByKinds(Set<ConfigNamespaceKind> kinds) {
        List<String> kindNames = kinds.stream().map(Enum::name).toList();
        return configurations.findByKindIn(kindNames).stream()
                .map(JpaConfigurationRepository::toDomain).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<ConfigVersion> history(String namespace, int limit) {
        return versions.findByNamespaceOrderByVersionDesc(namespace, PageRequest.of(0, limit)).stream()
                .map(JpaConfigurationRepository::toDomainVersion).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<ConfigVersion> versionAt(String namespace, long version) {
        return versions.findByNamespaceAndVersion(namespace, version)
                .map(JpaConfigurationRepository::toDomainVersion);
    }

    @Override
    @Transactional
    public void persist(Configuration current, ConfigVersion snapshot, ConfigChange change) {
        ConfigurationEntity entity = configurations.findById(current.namespace())
                .map(existing -> {
                    existing.apply(current.kind().name(), current.version(), current.valueJson(),
                            current.deleted(), current.lastActorId().orElse(null),
                            current.lastJustification().orElse(null), current.updatedAt());
                    return existing;
                })
                .orElseGet(() -> new ConfigurationEntity(current.namespace(), current.kind().name(),
                        current.version(), current.valueJson(), current.deleted(),
                        current.lastActorId().orElse(null), current.lastJustification().orElse(null),
                        current.updatedAt()));
        configurations.save(entity);
        versions.save(toVersionEntity(snapshot));
        changes.save(toChangeEntity(change));
    }

    private static ConfigVersionEntity toVersionEntity(ConfigVersion v) {
        return new ConfigVersionEntity(v.namespace(), v.version(), v.valueJson(), v.deleted(),
                v.actorId().orElse(null), v.justification().orElse(null), v.createdAt());
    }

    private static ConfigChangeEntity toChangeEntity(ConfigChange c) {
        return new ConfigChangeEntity(c.namespace(), c.fromVersion().orElse(null), c.toVersion(),
                c.beforeJson().orElse(null), c.afterJson(), c.actorId(), c.justification(), c.changedAt());
    }

    private static Configuration toDomain(ConfigurationEntity e) {
        return Configuration.reconstitute(e.namespace(), ConfigNamespaceKind.valueOf(e.kind()),
                e.version(), e.valueJson(), e.deleted(), e.lastActorId(), e.lastJustification(),
                e.updatedAt());
    }

    private static ConfigVersion toDomainVersion(ConfigVersionEntity e) {
        return new ConfigVersion(e.namespace(), e.version(), e.valueJson(), e.deleted(),
                Optional.ofNullable(e.actorId()), Optional.ofNullable(e.justification()), e.createdAt());
    }
}
