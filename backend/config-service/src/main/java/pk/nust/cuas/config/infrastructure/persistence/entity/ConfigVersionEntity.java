package pk.nust.cuas.config.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/** JPA mapping for {@code config_versions} — one entry per version of a namespace. */
@Entity
@Table(name = "config_versions")
public class ConfigVersionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "namespace", nullable = false)
    private String namespace;

    @Column(name = "version", nullable = false)
    private long version;

    @Column(name = "value_json", nullable = false)
    private String valueJson;

    @Column(name = "deleted", nullable = false)
    private boolean deleted;

    @Column(name = "actor_id")
    private String actorId;

    @Column(name = "justification")
    private String justification;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected ConfigVersionEntity() {
    }

    public ConfigVersionEntity(String namespace, long version, String valueJson, boolean deleted,
                               String actorId, String justification, Instant createdAt) {
        this.namespace = namespace;
        this.version = version;
        this.valueJson = valueJson;
        this.deleted = deleted;
        this.actorId = actorId;
        this.justification = justification;
        this.createdAt = createdAt;
    }

    public String namespace() {
        return namespace;
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

    public String actorId() {
        return actorId;
    }

    public String justification() {
        return justification;
    }

    public Instant createdAt() {
        return createdAt;
    }
}
