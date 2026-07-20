package pk.nust.cuas.audit.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Persistence shape of an audit row, deliberately separate from the domain
 * {@code AuditRecord} so the schema can change without the domain changing.
 *
 * <p>Mapped read-mostly: rows are inserted once and never updated. {@code details} is stored as
 * {@code JSONB} and {@code actor_ip} as {@code INET}, matching the section 7.5 schema; both are
 * written as strings and cast by the driver.
 */
@Entity
@Table(name = "audit_log")
public class AuditLogEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "seq", updatable = false)
    private Long seq;

    @Column(name = "audit_id", nullable = false, unique = true, updatable = false)
    private UUID auditId;

    @Column(name = "occurred_at", nullable = false, updatable = false)
    private Instant occurredAt;

    @Column(name = "recorded_at", nullable = false, updatable = false, insertable = false)
    private Instant recordedAt;

    @Column(name = "actor_id", nullable = false, updatable = false)
    private String actorId;

    @Column(name = "actor_type", nullable = false, updatable = false)
    private String actorType;

    @JdbcTypeCode(SqlTypes.INET)
    @Column(name = "actor_ip", updatable = false)
    private String actorIp;

    @Column(name = "session_id", updatable = false)
    private String sessionId;

    @Column(name = "action", nullable = false, updatable = false)
    private String action;

    @Column(name = "resource_type", nullable = false, updatable = false)
    private String resourceType;

    @Column(name = "resource_id", updatable = false)
    private String resourceId;

    @Column(name = "outcome", nullable = false, updatable = false)
    private String outcome;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "details", nullable = false, updatable = false)
    private String details;

    @Column(name = "correlation_id", updatable = false)
    private String correlationId;

    @Column(name = "prev_hash", nullable = false, updatable = false)
    private byte[] prevHash;

    @Column(name = "record_hash", nullable = false, updatable = false)
    private byte[] recordHash;

    /** Required by JPA, and used by the mapper in the sibling package to build a row for insert. */
    public AuditLogEntity() {
    }

    public Long getSeq() { return seq; }
    public UUID getAuditId() { return auditId; }
    public Instant getOccurredAt() { return occurredAt; }
    public Instant getRecordedAt() { return recordedAt; }
    public String getActorId() { return actorId; }
    public String getActorType() { return actorType; }
    public String getActorIp() { return actorIp; }
    public String getSessionId() { return sessionId; }
    public String getAction() { return action; }
    public String getResourceType() { return resourceType; }
    public String getResourceId() { return resourceId; }
    public String getOutcome() { return outcome; }
    public String getDetails() { return details; }
    public String getCorrelationId() { return correlationId; }
    public byte[] getPrevHash() { return prevHash; }
    public byte[] getRecordHash() { return recordHash; }

    public void setSeq(Long seq) { this.seq = seq; }
    public void setAuditId(UUID auditId) { this.auditId = auditId; }
    public void setOccurredAt(Instant occurredAt) { this.occurredAt = occurredAt; }
    public void setRecordedAt(Instant recordedAt) { this.recordedAt = recordedAt; }
    public void setActorId(String actorId) { this.actorId = actorId; }
    public void setActorType(String actorType) { this.actorType = actorType; }
    public void setActorIp(String actorIp) { this.actorIp = actorIp; }
    public void setSessionId(String sessionId) { this.sessionId = sessionId; }
    public void setAction(String action) { this.action = action; }
    public void setResourceType(String resourceType) { this.resourceType = resourceType; }
    public void setResourceId(String resourceId) { this.resourceId = resourceId; }
    public void setOutcome(String outcome) { this.outcome = outcome; }
    public void setDetails(String details) { this.details = details; }
    public void setCorrelationId(String correlationId) { this.correlationId = correlationId; }
    public void setPrevHash(byte[] prevHash) { this.prevHash = prevHash; }
    public void setRecordHash(byte[] recordHash) { this.recordHash = recordHash; }
}
