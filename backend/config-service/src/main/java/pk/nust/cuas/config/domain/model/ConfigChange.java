package pk.nust.cuas.config.domain.model;

import java.time.Instant;
import java.util.Optional;

/**
 * The audit record of one configuration change (ARCHITECTURE.md §4.1.12): what changed, from which
 * version to which, the before → after values, and — required, not optional — who did it and why.
 *
 * <p>Written in the same transaction as the change it records, so a change that cannot be audited does
 * not happen (§7.5, fail-closed). This is the config service's own tamper-evident-adjacent trail; the
 * platform audit chain additionally mirrors the config topics.
 *
 * @param namespace     the namespace changed
 * @param fromVersion   the version before, empty for the first write
 * @param toVersion     the version after
 * @param beforeJson    the value before, empty for the first write
 * @param afterJson     the value after
 * @param actorId       who made the change
 * @param justification why — a configuration change is a critical action and PUT requires it
 * @param changedAt     when
 */
public record ConfigChange(String namespace, Optional<Long> fromVersion, long toVersion,
                           Optional<String> beforeJson, String afterJson, String actorId,
                           String justification, Instant changedAt) {

    public ConfigChange {
        if (namespace == null || afterJson == null) {
            throw new IllegalArgumentException("namespace and afterJson are required");
        }
        if (actorId == null || actorId.isBlank()) {
            throw new IllegalArgumentException("actorId is required — a change is recorded against a person");
        }
        if (justification == null || justification.isBlank()) {
            throw new IllegalArgumentException("justification is required for a configuration change");
        }
        if (fromVersion == null || beforeJson == null) {
            throw new IllegalArgumentException("use Optional.empty(), not null");
        }
    }

    /** The change from a previous configuration (or none) to a new one. */
    public static ConfigChange between(Optional<Configuration> before, Configuration after,
                                       String actorId, String justification) {
        return new ConfigChange(after.namespace(), before.map(Configuration::version),
                after.version(), before.map(Configuration::valueJson), after.valueJson(),
                actorId, justification, after.updatedAt());
    }
}
