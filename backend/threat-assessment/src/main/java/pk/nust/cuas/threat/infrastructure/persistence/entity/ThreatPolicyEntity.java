package pk.nust.cuas.threat.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * JPA mapping for a {@code threat_policies} row: a policy's identity and which version is in force.
 *
 * <p>The only mutable field is {@code activeVersion}. Publishing a policy change inserts a new
 * {@link PolicyVersionEntity} and repoints this row at it; nothing about an existing version is ever
 * rewritten.
 */
@Entity
@Table(name = "threat_policies")
public class ThreatPolicyEntity {

    @Id
    @Column(name = "policy_id")
    private String policyId;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "description", nullable = false)
    private String description;

    @Column(name = "active_version", nullable = false)
    private String activeVersion;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected ThreatPolicyEntity() {
    }

    public ThreatPolicyEntity(String policyId, String name, String description,
                              String activeVersion, Instant updatedAt) {
        this.policyId = policyId;
        this.name = name;
        this.description = description;
        this.activeVersion = activeVersion;
        this.updatedAt = updatedAt;
    }

    /** Points this policy at a newly published version. */
    public void activate(String version, Instant at) {
        this.activeVersion = version;
        this.updatedAt = at;
    }

    public String getPolicyId() {
        return policyId;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public String getActiveVersion() {
        return activeVersion;
    }
}
