package pk.nust.cuas.auth.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** An authenticated session. Revoked on logout, on deactivation, and on refresh-token reuse. */
@Entity
@Table(name = "sessions")
public class SessionEntity {

    @Id
    @Column(name = "id", updatable = false)
    private UUID id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @Column(name = "user_agent")
    private String userAgent;

    public SessionEntity() {
    }

    public UUID getId() { return id; }
    public UUID getUserId() { return userId; }
    public Instant getRevokedAt() { return revokedAt; }
    public String getUserAgent() { return userAgent; }

    public void setId(UUID id) { this.id = id; }
    public void setUserId(UUID userId) { this.userId = userId; }
    public void setRevokedAt(Instant revokedAt) { this.revokedAt = revokedAt; }
    public void setUserAgent(String userAgent) { this.userAgent = userAgent; }
}
