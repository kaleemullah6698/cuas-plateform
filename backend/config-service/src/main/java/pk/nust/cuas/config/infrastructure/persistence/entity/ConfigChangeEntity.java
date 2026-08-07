package pk.nust.cuas.config.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/** JPA mapping for {@code config_change_log} — the before/after audit record of one change. */
@Entity
@Table(name = "config_change_log")
public class ConfigChangeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "namespace", nullable = false)
    private String namespace;

    @Column(name = "from_version")
    private Long fromVersion;

    @Column(name = "to_version", nullable = false)
    private long toVersion;

    @Column(name = "before_json")
    private String beforeJson;

    @Column(name = "after_json", nullable = false)
    private String afterJson;

    @Column(name = "actor_id", nullable = false)
    private String actorId;

    @Column(name = "justification", nullable = false)
    private String justification;

    @Column(name = "changed_at", nullable = false)
    private Instant changedAt;

    protected ConfigChangeEntity() {
    }

    public ConfigChangeEntity(String namespace, Long fromVersion, long toVersion, String beforeJson,
                              String afterJson, String actorId, String justification, Instant changedAt) {
        this.namespace = namespace;
        this.fromVersion = fromVersion;
        this.toVersion = toVersion;
        this.beforeJson = beforeJson;
        this.afterJson = afterJson;
        this.actorId = actorId;
        this.justification = justification;
        this.changedAt = changedAt;
    }
}
