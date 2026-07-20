package pk.nust.cuas.auth.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/** Every authentication attempt, successful or not. Immutable evidence (section 7.5). */
@Entity
@Table(name = "login_attempts")
public class LoginAttemptEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "username", nullable = false, updatable = false)
    private String username;

    @Column(name = "succeeded", nullable = false, updatable = false)
    private boolean succeeded;

    @Column(name = "failure_reason", updatable = false)
    private String failureReason;

    @Column(name = "attempted_at", insertable = false, updatable = false)
    private Instant attemptedAt;

    public LoginAttemptEntity() {
    }

    public Long getId() { return id; }
    public String getUsername() { return username; }
    public boolean isSucceeded() { return succeeded; }
    public String getFailureReason() { return failureReason; }
    public Instant getAttemptedAt() { return attemptedAt; }

    public void setUsername(String username) { this.username = username; }
    public void setSucceeded(boolean succeeded) { this.succeeded = succeeded; }
    public void setFailureReason(String failureReason) { this.failureReason = failureReason; }
}
