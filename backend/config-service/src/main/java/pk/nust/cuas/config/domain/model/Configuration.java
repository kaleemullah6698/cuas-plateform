package pk.nust.cuas.config.domain.model;

import java.time.Instant;
import java.util.Optional;

/**
 * A configuration namespace's current state (ARCHITECTURE.md §4.1.12). The aggregate root.
 *
 * <p>Immutable (CLAUDE.md §3): every change returns a new {@code Configuration} with the version
 * incremented, so the before and after are both available to the caller that snapshots, diffs,
 * persists and publishes them. The value is opaque canonical JSON here — structural validation is an
 * application concern, because the domain must compile without a JSON library (CLAUDE.md §2).
 *
 * <p>The version is monotonic and never reused: a rollback does not restore an old version number, it
 * publishes a new, higher one carrying the old value, so the history is an append-only record of what
 * the configuration was at every point and a compacted topic keyed by namespace always converges on
 * the latest.
 */
public final class Configuration {

    private final String namespace;
    private final ConfigNamespaceKind kind;
    private final long version;
    private final String valueJson;
    private final boolean deleted;
    private final Optional<String> lastActorId;
    private final Optional<String> lastJustification;
    private final Instant updatedAt;

    private Configuration(String namespace, ConfigNamespaceKind kind, long version, String valueJson,
                          boolean deleted, Optional<String> lastActorId,
                          Optional<String> lastJustification, Instant updatedAt) {
        this.namespace = namespace;
        this.kind = kind;
        this.version = version;
        this.valueJson = valueJson;
        this.deleted = deleted;
        this.lastActorId = lastActorId;
        this.lastJustification = lastJustification;
        this.updatedAt = updatedAt;
    }

    /** Opens a namespace at version 1 with its first value. */
    public static Configuration initial(String namespace, ConfigNamespaceKind kind, String valueJson,
                                        String actorId, String justification, Instant at) {
        requireText(namespace, "namespace");
        requireText(valueJson, "valueJson");
        if (kind == null) {
            throw new IllegalArgumentException("kind is required");
        }
        return new Configuration(namespace, kind, 1L, valueJson, false,
                Optional.ofNullable(actorId), Optional.ofNullable(justification), at);
    }

    /** Reconstitutes from storage. */
    public static Configuration reconstitute(String namespace, ConfigNamespaceKind kind, long version,
                                             String valueJson, boolean deleted, String lastActorId,
                                             String lastJustification, Instant updatedAt) {
        return new Configuration(namespace, kind, version, valueJson, deleted,
                Optional.ofNullable(lastActorId), Optional.ofNullable(lastJustification), updatedAt);
    }

    /** Applies a new value, advancing the version. */
    public Configuration updatedWith(String valueJson, String actorId, String justification, Instant at) {
        requireText(valueJson, "valueJson");
        return new Configuration(namespace, kind, version + 1, valueJson, false,
                Optional.ofNullable(actorId), Optional.ofNullable(justification), at);
    }

    /** Marks the namespace deleted, advancing the version; the last value is retained for recall. */
    public Configuration deletedBy(String actorId, String justification, Instant at) {
        return new Configuration(namespace, kind, version + 1, valueJson, true,
                Optional.ofNullable(actorId), Optional.ofNullable(justification), at);
    }

    /** Restores a past version's value as a new, higher version — the rollback (§4.1.12). */
    public Configuration restoredFrom(ConfigVersion target, String actorId, String justification,
                                      Instant at) {
        return new Configuration(namespace, kind, version + 1, target.valueJson(), target.deleted(),
                Optional.ofNullable(actorId), Optional.ofNullable(justification), at);
    }

    public String namespace() {
        return namespace;
    }

    public ConfigNamespaceKind kind() {
        return kind;
    }

    public long version() {
        return version;
    }

    public String valueJson() {
        return valueJson;
    }

    public boolean deleted() {
        return deleted;
    }

    public Optional<String> lastActorId() {
        return lastActorId;
    }

    public Optional<String> lastJustification() {
        return lastJustification;
    }

    public Instant updatedAt() {
        return updatedAt;
    }

    private static void requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
    }
}
