package pk.nust.cuas.config.domain.model;

import java.time.Instant;
import java.util.Optional;

/**
 * One point in a namespace's history (ARCHITECTURE.md §4.1.12): the full value it held at a version,
 * appended on every change so any past state can be recalled and rolled back to.
 *
 * @param namespace     the namespace
 * @param version       the version this snapshot records
 * @param valueJson     the value at that version, as canonical JSON
 * @param deleted       whether the namespace was deleted at that version
 * @param actorId       who made the change, if recorded
 * @param justification why, if recorded
 * @param createdAt     when the version was written
 */
public record ConfigVersion(String namespace, long version, String valueJson, boolean deleted,
                            Optional<String> actorId, Optional<String> justification, Instant createdAt) {

    public ConfigVersion {
        if (namespace == null || valueJson == null) {
            throw new IllegalArgumentException("namespace and valueJson are required");
        }
        if (actorId == null || justification == null) {
            throw new IllegalArgumentException("use Optional.empty(), not null");
        }
    }

    /** Snapshots a configuration's current state as a history entry. */
    public static ConfigVersion of(Configuration configuration) {
        return new ConfigVersion(configuration.namespace(), configuration.version(),
                configuration.valueJson(), configuration.deleted(), configuration.lastActorId(),
                configuration.lastJustification(), configuration.updatedAt());
    }
}
