package pk.nust.cuas.auth.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * Server-side proof that the first factor succeeded.
 *
 * <p>Held here rather than handed to the client as a signed blob, so the second factor cannot be
 * bypassed by fabricating or replaying client-side state.
 */
@Entity
@Table(name = "mfa_challenges")
public class MfaChallengeEntity {

    @Id
    @Column(name = "id", updatable = false)
    private UUID id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "expires_at", nullable = false, updatable = false)
    private Instant expiresAt;

    @Column(name = "consumed_at")
    private Instant consumedAt;

    public MfaChallengeEntity() {
    }

    public UUID getId() { return id; }
    public UUID getUserId() { return userId; }
    public Instant getExpiresAt() { return expiresAt; }
    public Instant getConsumedAt() { return consumedAt; }

    public void setId(UUID id) { this.id = id; }
    public void setUserId(UUID userId) { this.userId = userId; }
    public void setExpiresAt(Instant expiresAt) { this.expiresAt = expiresAt; }
    public void setConsumedAt(Instant consumedAt) { this.consumedAt = consumedAt; }
}
