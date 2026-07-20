package pk.nust.cuas.user.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * Service-local record of a role change and its justification (ARCHITECTURE.md section 7.4).
 * The authoritative, tamper-evident copy lives in audit-service.
 */
@Entity
@Table(name = "role_change_log")
public class RoleChangeLogEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "role_code", nullable = false, updatable = false)
    private String roleCode;

    @Column(name = "change_type", nullable = false, updatable = false)
    private String changeType;

    @Column(name = "justification", nullable = false, updatable = false)
    private String justification;

    @Column(name = "actor_id", nullable = false, updatable = false)
    private String actorId;

    @Column(name = "occurred_at", insertable = false, updatable = false)
    private Instant occurredAt;

    public RoleChangeLogEntity() {
    }

    public Long getId() { return id; }
    public UUID getUserId() { return userId; }
    public String getRoleCode() { return roleCode; }
    public String getChangeType() { return changeType; }
    public String getJustification() { return justification; }
    public String getActorId() { return actorId; }
    public Instant getOccurredAt() { return occurredAt; }

    public void setUserId(UUID userId) { this.userId = userId; }
    public void setRoleCode(String roleCode) { this.roleCode = roleCode; }
    public void setChangeType(String changeType) { this.changeType = changeType; }
    public void setJustification(String justification) { this.justification = justification; }
    public void setActorId(String actorId) { this.actorId = actorId; }
}
