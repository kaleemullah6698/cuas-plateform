package pk.nust.cuas.auth.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * A TOTP shared secret and the highest time step already accepted for it.
 *
 * <p>The step is what makes a code single-use: a code remains valid for its whole 30-second
 * window, so without recording the step an observer could replay one within that window.
 */
@Entity
@Table(name = "mfa_secrets")
public class MfaSecretEntity {

    @Id
    @Column(name = "user_id", updatable = false)
    private UUID userId;

    @Column(name = "secret", nullable = false)
    private String secret;

    @Column(name = "enabled", nullable = false)
    private boolean enabled;

    @Column(name = "last_used_step")
    private Long lastUsedStep;

    @Column(name = "confirmed_at")
    private Instant confirmedAt;

    public MfaSecretEntity() {
    }

    public UUID getUserId() { return userId; }
    public String getSecret() { return secret; }
    public boolean isEnabled() { return enabled; }
    public Long getLastUsedStep() { return lastUsedStep; }
    public Instant getConfirmedAt() { return confirmedAt; }

    public void setUserId(UUID userId) { this.userId = userId; }
    public void setSecret(String secret) { this.secret = secret; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public void setLastUsedStep(Long lastUsedStep) { this.lastUsedStep = lastUsedStep; }
    public void setConfirmedAt(Instant confirmedAt) { this.confirmedAt = confirmedAt; }
}
