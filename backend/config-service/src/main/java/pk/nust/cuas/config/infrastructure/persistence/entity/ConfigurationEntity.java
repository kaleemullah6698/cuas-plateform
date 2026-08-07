package pk.nust.cuas.config.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;

/** JPA mapping for {@code configurations} — a namespace's current state. */
@Entity
@Table(name = "configurations")
public class ConfigurationEntity {

    @Id
    @Column(name = "namespace")
    private String namespace;

    @Column(name = "kind", nullable = false)
    private String kind;

    @Column(name = "version", nullable = false)
    private long version;

    @Column(name = "value_json", nullable = false)
    private String valueJson;

    @Column(name = "deleted", nullable = false)
    private boolean deleted;

    @Column(name = "last_actor_id")
    private String lastActorId;

    @Column(name = "last_justification")
    private String lastJustification;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(name = "opt_version", nullable = false)
    private long optVersion;

    protected ConfigurationEntity() {
    }

    public ConfigurationEntity(String namespace, String kind, long version, String valueJson,
                               boolean deleted, String lastActorId, String lastJustification,
                               Instant updatedAt) {
        this.namespace = namespace;
        this.kind = kind;
        this.version = version;
        this.valueJson = valueJson;
        this.deleted = deleted;
        this.lastActorId = lastActorId;
        this.lastJustification = lastJustification;
        this.updatedAt = updatedAt;
    }

    /** Advances the current state to a new version. */
    public void apply(String kind, long version, String valueJson, boolean deleted, String lastActorId,
                      String lastJustification, Instant updatedAt) {
        this.kind = kind;
        this.version = version;
        this.valueJson = valueJson;
        this.deleted = deleted;
        this.lastActorId = lastActorId;
        this.lastJustification = lastJustification;
        this.updatedAt = updatedAt;
    }

    public String namespace() {
        return namespace;
    }

    public String kind() {
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

    public String lastActorId() {
        return lastActorId;
    }

    public String lastJustification() {
        return lastJustification;
    }

    public Instant updatedAt() {
        return updatedAt;
    }
}
