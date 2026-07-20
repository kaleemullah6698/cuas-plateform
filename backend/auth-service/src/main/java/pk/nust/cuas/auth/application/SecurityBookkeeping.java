package pk.nust.cuas.auth.application;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import pk.nust.cuas.auth.infrastructure.persistence.entity.CredentialEntity;
import pk.nust.cuas.auth.infrastructure.persistence.entity.LoginAttemptEntity;
import pk.nust.cuas.auth.infrastructure.persistence.repository.CredentialRepository;
import pk.nust.cuas.auth.infrastructure.persistence.repository.LoginAttemptRepository;
import pk.nust.cuas.auth.infrastructure.persistence.repository.RefreshTokenRepository;
import pk.nust.cuas.auth.infrastructure.persistence.repository.SessionRepository;

/**
 * Records the security facts that must survive a failed request.
 *
 * <p>Every method here runs in its own transaction ({@link Propagation#REQUIRES_NEW}).
 *
 * <p>This is not incidental. A failed login ends by throwing, which rolls the caller's transaction
 * back — and if the failure bookkeeping shared that transaction it would be rolled back too. The
 * consequences are severe and silent: the attempt would never appear in {@code login_attempts},
 * the failure counter would never increment so lockout could never trigger, and — worst — the
 * family revocation performed on refresh-token reuse would be undone by the very exception that
 * rejects the reused token, leaving the captured token's family live. Reuse detection would report
 * an attack and then take no action.
 *
 * <p>Being a separate bean is also required, not stylistic: a self-invoked {@code REQUIRES_NEW}
 * method does not pass through the transactional proxy and would silently join the caller's
 * transaction instead.
 */
@Component
public class SecurityBookkeeping {

    private static final Logger log = LoggerFactory.getLogger(SecurityBookkeeping.class);

    private final CredentialRepository credentials;
    private final LoginAttemptRepository loginAttempts;
    private final RefreshTokenRepository refreshTokens;
    private final SessionRepository sessions;
    private final int maxFailedAttempts;
    private final Duration lockoutDuration;

    public SecurityBookkeeping(CredentialRepository credentials,
                               LoginAttemptRepository loginAttempts,
                               RefreshTokenRepository refreshTokens,
                               SessionRepository sessions,
                               @Value("${auth.lockout.max-failed-attempts:5}") int maxFailedAttempts,
                               @Value("${auth.lockout.duration:PT15M}") Duration lockoutDuration) {
        this.credentials = credentials;
        this.loginAttempts = loginAttempts;
        this.refreshTokens = refreshTokens;
        this.sessions = sessions;
        this.maxFailedAttempts = maxFailedAttempts;
        this.lockoutDuration = lockoutDuration;
    }

    /**
     * Records one authentication attempt.
     *
     * @param username      the username presented, whether or not it exists
     * @param succeeded     the outcome
     * @param failureReason why it failed, or {@code null} on success
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordAttempt(String username, boolean succeeded, String failureReason) {
        LoginAttemptEntity attempt = new LoginAttemptEntity();
        attempt.setUsername(username);
        attempt.setSucceeded(succeeded);
        attempt.setFailureReason(failureReason);
        loginAttempts.saveAndFlush(attempt);
    }

    /**
     * Increments the failure counter and locks the account once the threshold is reached.
     *
     * @param userId the account that failed
     * @return the instant the account is locked until, or empty when it is not locked
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public java.util.Optional<Instant> registerFailure(UUID userId) {
        CredentialEntity credential = credentials.findById(userId).orElse(null);
        if (credential == null) {
            return java.util.Optional.empty();
        }

        credential.setFailedAttempts(credential.getFailedAttempts() + 1);
        if (credential.getFailedAttempts() >= maxFailedAttempts) {
            credential.setLockedUntil(Instant.now().plus(lockoutDuration));
            log.warn("Account {} locked after {} failed attempts",
                    userId, credential.getFailedAttempts());
        }
        credentials.saveAndFlush(credential);
        return java.util.Optional.ofNullable(credential.getLockedUntil());
    }

    /** Clears the failure counter after a successful first factor. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void clearFailures(UUID userId) {
        credentials.findById(userId).ifPresent(credential -> {
            credential.setFailedAttempts(0);
            credential.setLockedUntil(null);
            credentials.saveAndFlush(credential);
        });
    }

    /**
     * Revokes a refresh-token family and its session after reuse was detected.
     *
     * <p>Must outlive the rejection: the caller throws immediately afterwards, and a revocation
     * rolled back with that exception would leave the captured token usable.
     *
     * @param familyId  the compromised family
     * @param sessionId the session it backs
     * @return how many family members were revoked
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int revokeCompromisedFamily(UUID familyId, UUID sessionId) {
        Instant now = Instant.now();
        int revoked = refreshTokens.revokeFamily(familyId, now, "REUSE_DETECTED");
        sessions.findById(sessionId).ifPresent(session -> {
            session.setRevokedAt(now);
            sessions.saveAndFlush(session);
        });
        return revoked;
    }
}
