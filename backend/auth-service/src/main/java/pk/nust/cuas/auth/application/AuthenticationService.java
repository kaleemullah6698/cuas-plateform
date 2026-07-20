package pk.nust.cuas.auth.application;

import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pk.nust.cuas.auth.domain.exception.AccountLockedException;
import pk.nust.cuas.auth.domain.exception.AuthenticationFailedException;
import pk.nust.cuas.auth.domain.exception.InvalidRefreshTokenException;
import pk.nust.cuas.auth.domain.exception.MfaVerificationFailedException;
import pk.nust.cuas.auth.domain.model.TotpGenerator;
import pk.nust.cuas.auth.infrastructure.persistence.entity.CredentialEntity;
import pk.nust.cuas.auth.infrastructure.persistence.entity.MfaChallengeEntity;
import pk.nust.cuas.auth.infrastructure.persistence.entity.MfaSecretEntity;
import pk.nust.cuas.auth.infrastructure.persistence.entity.RefreshTokenEntity;
import pk.nust.cuas.auth.infrastructure.persistence.entity.SessionEntity;
import pk.nust.cuas.auth.infrastructure.persistence.repository.CredentialRepository;
import pk.nust.cuas.auth.infrastructure.persistence.repository.MfaChallengeRepository;
import pk.nust.cuas.auth.infrastructure.persistence.repository.MfaSecretRepository;
import pk.nust.cuas.auth.infrastructure.persistence.repository.RefreshTokenRepository;
import pk.nust.cuas.auth.infrastructure.persistence.repository.SessionRepository;
import pk.nust.cuas.auth.infrastructure.token.AccessTokenIssuer;
import pk.nust.cuas.auth.infrastructure.user.PermissionResolver;
import pk.nust.cuas.commons.audit.ActorType;
import pk.nust.cuas.commons.audit.AuditActions;
import pk.nust.cuas.commons.audit.AuditEvent;
import pk.nust.cuas.commons.audit.AuditOutcome;
import pk.nust.cuas.commons.audit.AuditPublisher;
import pk.nust.cuas.commons.correlation.CorrelationId;

/**
 * The authentication flows of ARCHITECTURE.md section 7.3.
 *
 * <p>Every path through this class produces an audit event, successful or not: section 7.5 makes
 * authentication attempts mandatory audit events, and a failed login is the more interesting of
 * the two. Publishing is fail-closed, so an attempt that cannot be recorded is refused.
 *
 * <p>Failure responses are deliberately uniform. Unknown username, wrong password and deactivated
 * account all raise {@link AuthenticationFailedException} with the same message, because
 * distinguishing them would let an unauthenticated caller enumerate accounts.
 */
@Service
public class AuthenticationService {

    private static final Logger log = LoggerFactory.getLogger(AuthenticationService.class);
    private static final String RESOURCE_SESSION = "SESSION";
    private static final int TOKEN_BYTES = 32;

    private final CredentialRepository credentials;
    private final MfaSecretRepository mfaSecrets;
    private final MfaChallengeRepository mfaChallenges;
    private final RefreshTokenRepository refreshTokens;
    private final SessionRepository sessions;
    private final SecurityBookkeeping bookkeeping;
    private final PasswordEncoder passwordEncoder;
    private final TotpGenerator totp;
    private final AccessTokenIssuer accessTokenIssuer;
    private final PermissionResolver permissionResolver;
    private final AuditPublisher audit;

    private final Duration refreshTokenTtl;
    private final Duration mfaChallengeTtl;

    private final java.security.SecureRandom random = new java.security.SecureRandom();

    @SuppressWarnings("java:S107") // the flows genuinely need each of these collaborators
    public AuthenticationService(CredentialRepository credentials,
                                 MfaSecretRepository mfaSecrets,
                                 MfaChallengeRepository mfaChallenges,
                                 RefreshTokenRepository refreshTokens,
                                 SessionRepository sessions,
                                 SecurityBookkeeping bookkeeping,
                                 PasswordEncoder passwordEncoder,
                                 TotpGenerator totp,
                                 AccessTokenIssuer accessTokenIssuer,
                                 PermissionResolver permissionResolver,
                                 AuditPublisher audit,
                                 @Value("${auth.jwt.refresh-token-ttl:P7D}") Duration refreshTokenTtl,
                                 @Value("${auth.mfa.challenge-ttl:PT5M}") Duration mfaChallengeTtl) {
        this.credentials = credentials;
        this.mfaSecrets = mfaSecrets;
        this.mfaChallenges = mfaChallenges;
        this.refreshTokens = refreshTokens;
        this.sessions = sessions;
        this.bookkeeping = bookkeeping;
        this.passwordEncoder = passwordEncoder;
        this.totp = totp;
        this.accessTokenIssuer = accessTokenIssuer;
        this.permissionResolver = permissionResolver;
        this.audit = audit;
        this.refreshTokenTtl = refreshTokenTtl;
        this.mfaChallengeTtl = mfaChallengeTtl;
    }

    /**
     * Verifies the first factor.
     *
     * @param username presented username
     * @param password presented password
     * @return an MFA challenge when the account has MFA enabled, otherwise an issued token pair
     */
    @Transactional
    public LoginResult login(String username, String password) {
        Instant now = Instant.now();
        CredentialEntity credential = credentials.findByUsername(username).orElse(null);

        if (credential == null) {
            // Still spend the cost of a hash so a missing account is not distinguishable from a
            // wrong password by how quickly the request comes back.
            passwordEncoder.matches(password, "$argon2id$v=19$m=16,t=2,p=1$c29tZXNhbHQ$dummy");
            bookkeeping.recordAttempt(username, false, "UNKNOWN_USERNAME");
            auditAuth(AuditActions.LOGIN_FAILED, username, null, AuditOutcome.FAILURE,
                    Map.of("reason", "UNKNOWN_USERNAME"));
            throw new AuthenticationFailedException();
        }

        if (credential.getLockedUntil() != null && now.isBefore(credential.getLockedUntil())) {
            bookkeeping.recordAttempt(username, false, "LOCKED");
            auditAuth(AuditActions.LOGIN_FAILED, username, credential.getUserId(),
                    AuditOutcome.DENIED, Map.of("reason", "ACCOUNT_LOCKED"));
            throw new AccountLockedException(credential.getLockedUntil());
        }

        if (!passwordEncoder.matches(password, credential.getPasswordHash())) {
            bookkeeping.registerFailure(credential.getUserId());
            bookkeeping.recordAttempt(username, false, "BAD_PASSWORD");
            auditAuth(AuditActions.LOGIN_FAILED, username, credential.getUserId(),
                    AuditOutcome.FAILURE, Map.of("reason", "BAD_PASSWORD",
                            "failedAttempts", String.valueOf(credential.getFailedAttempts())));
            throw new AuthenticationFailedException();
        }

        // The password was right: clear the failure counter before going further.
        bookkeeping.clearFailures(credential.getUserId());

        Optional<MfaSecretEntity> secret = mfaSecrets.findById(credential.getUserId())
                .filter(MfaSecretEntity::isEnabled);

        if (secret.isPresent()) {
            MfaChallengeEntity challenge = new MfaChallengeEntity();
            challenge.setId(UUID.randomUUID());
            challenge.setUserId(credential.getUserId());
            challenge.setExpiresAt(now.plus(mfaChallengeTtl));
            mfaChallenges.save(challenge);

            auditAuth(AuditActions.MFA_CHALLENGE_ISSUED, username, credential.getUserId(),
                    AuditOutcome.SUCCESS, Map.of());
            return LoginResult.mfaRequired(challenge.getId());
        }

        bookkeeping.recordAttempt(username, true, null);
        auditAuth(AuditActions.LOGIN_SUCCEEDED, username, credential.getUserId(),
                AuditOutcome.SUCCESS, Map.of("mfa", "false"));
        return LoginResult.authenticated(issueTokens(credential.getUserId(), now));
    }

    /**
     * Verifies the second factor and completes the login.
     *
     * @param challengeId the challenge issued by {@link #login}
     * @param code        the presented TOTP code
     * @return the issued token pair
     */
    @Transactional
    public TokenPair verifyMfa(UUID challengeId, String code) {
        Instant now = Instant.now();
        MfaChallengeEntity challenge = mfaChallenges.findById(challengeId).orElse(null);

        if (challenge == null || challenge.getConsumedAt() != null
                || now.isAfter(challenge.getExpiresAt())) {
            auditAuth(AuditActions.MFA_VERIFICATION_FAILED, "unknown", null,
                    AuditOutcome.FAILURE, Map.of("reason", "INVALID_OR_EXPIRED_CHALLENGE"));
            throw new MfaVerificationFailedException();
        }

        MfaSecretEntity secret = mfaSecrets.findById(challenge.getUserId()).orElse(null);
        if (secret == null || !secret.isEnabled()) {
            throw new MfaVerificationFailedException();
        }

        Optional<Long> matchedStep =
                totp.verify(secret.getSecret(), code, now, secret.getLastUsedStep());
        if (matchedStep.isEmpty()) {
            auditAuth(AuditActions.MFA_VERIFICATION_FAILED, "user", challenge.getUserId(),
                    AuditOutcome.FAILURE, Map.of("reason", "BAD_CODE"));
            throw new MfaVerificationFailedException();
        }

        // Record the step so the same code cannot be presented again inside its window, and
        // consume the challenge so it cannot be replayed either.
        secret.setLastUsedStep(matchedStep.get());
        mfaSecrets.save(secret);
        challenge.setConsumedAt(now);
        mfaChallenges.save(challenge);

        bookkeeping.recordAttempt("user:" + challenge.getUserId(), true, null);
        auditAuth(AuditActions.MFA_VERIFICATION_SUCCEEDED, "user", challenge.getUserId(),
                AuditOutcome.SUCCESS, Map.of());
        auditAuth(AuditActions.LOGIN_SUCCEEDED, "user", challenge.getUserId(),
                AuditOutcome.SUCCESS, Map.of("mfa", "true"));
        return issueTokens(challenge.getUserId(), now);
    }

    /**
     * Exchanges a refresh token for a new pair, rotating the family.
     *
     * <p>This is where reuse detection lives (section 7.3). A member that has already been
     * exchanged should never be presented again; if it is, the token was captured, so the whole
     * family is revoked and the caller is refused — including the attacker and the legitimate
     * holder, which is the intended outcome: the login is no longer trustworthy.
     *
     * @param presentedToken the opaque refresh token
     * @return a freshly rotated token pair
     */
    @Transactional
    public TokenPair refresh(String presentedToken) {
        Instant now = Instant.now();
        RefreshTokenEntity stored = refreshTokens.findByTokenHash(hash(presentedToken))
                .orElseThrow(InvalidRefreshTokenException::new);

        if (stored.getUsedAt() != null) {
            // Committed in its own transaction: this method throws immediately below, and a
            // revocation rolled back with that exception would leave the captured family live.
            int revoked = bookkeeping.revokeCompromisedFamily(
                    stored.getFamilyId(), stored.getSessionId());

            log.warn("Refresh token reuse detected for user {}; revoked {} family members",
                    stored.getUserId(), revoked);
            auditAuth(AuditActions.TOKEN_REUSE_DETECTED, "user", stored.getUserId(),
                    AuditOutcome.DENIED, Map.of(
                            "familyId", stored.getFamilyId().toString(),
                            "revokedMembers", String.valueOf(revoked)));
            throw new InvalidRefreshTokenException();
        }

        if (stored.getRevokedAt() != null || now.isAfter(stored.getExpiresAt())) {
            throw new InvalidRefreshTokenException();
        }

        stored.setUsedAt(now);
        refreshTokens.save(stored);

        TokenPair rotated = issueTokens(stored.getUserId(), now,
                stored.getFamilyId(), stored.getSessionId());
        auditAuth(AuditActions.TOKEN_REFRESHED, "user", stored.getUserId(),
                AuditOutcome.SUCCESS, Map.of("familyId", stored.getFamilyId().toString()));
        return rotated;
    }

    /**
     * Ends one session by revoking its refresh-token family.
     *
     * @param presentedToken the refresh token identifying the session
     */
    @Transactional
    public void logout(String presentedToken) {
        Instant now = Instant.now();
        refreshTokens.findByTokenHash(hash(presentedToken)).ifPresent(stored -> {
            refreshTokens.revokeFamily(stored.getFamilyId(), now, "LOGOUT");
            sessions.findById(stored.getSessionId()).ifPresent(session -> {
                session.setRevokedAt(now);
                sessions.save(session);
            });
            auditAuth(AuditActions.LOGOUT, "user", stored.getUserId(),
                    AuditOutcome.SUCCESS, Map.of());
        });
    }

    /**
     * Revokes every session and refresh-token family for a user.
     *
     * <p>Invoked by the operator through logout-all, and by the deactivation consumer so a
     * disabled account stops acting immediately rather than when its access token expires.
     *
     * @param userId the user whose access is being withdrawn
     * @param reason why
     */
    @Transactional
    public void revokeAllForUser(UUID userId, String reason) {
        Instant now = Instant.now();
        int tokens = refreshTokens.revokeAllForUser(userId, now, reason);
        int revokedSessions = sessions.revokeAllForUser(userId, now);

        auditAuth(AuditActions.TOKEN_REVOKED, "user", userId, AuditOutcome.SUCCESS,
                Map.of("reason", reason,
                        "revokedTokens", String.valueOf(tokens),
                        "revokedSessions", String.valueOf(revokedSessions)));
        log.info("Revoked {} tokens and {} sessions for user {} ({})",
                tokens, revokedSessions, userId, reason);
    }

    private TokenPair issueTokens(UUID userId, Instant now) {
        return issueTokens(userId, now, UUID.randomUUID(), null);
    }

    private TokenPair issueTokens(UUID userId, Instant now, UUID familyId, UUID existingSessionId) {
        PermissionResolver.Resolution resolution = permissionResolver.resolve(userId);
        if (!resolution.active()) {
            // A deactivated account must not receive a token even if it authenticated a moment ago.
            throw new AuthenticationFailedException();
        }

        UUID sessionId = existingSessionId;
        if (sessionId == null) {
            SessionEntity session = new SessionEntity();
            session.setId(UUID.randomUUID());
            session.setUserId(userId);
            sessions.save(session);
            sessionId = session.getId();
        }

        AccessTokenIssuer.IssuedToken accessToken = accessTokenIssuer.issue(
                userId, sessionId, resolution.roles(), resolution.permissions(), now);

        String refreshValue = newOpaqueToken();
        RefreshTokenEntity refresh = new RefreshTokenEntity();
        refresh.setId(UUID.randomUUID());
        refresh.setFamilyId(familyId);
        refresh.setUserId(userId);
        refresh.setTokenHash(hash(refreshValue));
        refresh.setSessionId(sessionId);
        refresh.setExpiresAt(now.plus(refreshTokenTtl));
        refreshTokens.save(refresh);

        return new TokenPair(accessToken.value(), accessToken.expiresAt(),
                refreshValue, refresh.getExpiresAt(), sessionId);
    }

    private void auditAuth(String action, String username, UUID userId, AuditOutcome outcome,
                           Map<String, String> details) {
        Map<String, String> enriched = new java.util.HashMap<>(details);
        enriched.put("username", username);
        audit.publish(AuditEvent.of(action, RESOURCE_SESSION)
                .actor(userId != null ? userId.toString() : "anonymous",
                        userId != null ? ActorType.USER : ActorType.SYSTEM)
                .resourceId(userId != null ? userId.toString() : null)
                .outcome(outcome)
                .details(enriched)
                .correlationId(CorrelationId.current().orElse(null))
                .build());
    }

    /** @return a 256-bit opaque token, URL-safe */
    private String newOpaqueToken() {
        byte[] bytes = new byte[TOKEN_BYTES];
        random.nextBytes(bytes);
        return java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /**
     * Digests a refresh token for storage. SHA-256 rather than a password hash: the token is
     * already 256 bits of entropy, so it is not guessable and needs no work factor — but storing
     * it in the clear would make a database disclosure directly usable.
     */
    private String hash(String token) {
        try {
            var digest = java.security.MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(
                    digest.digest(token.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is unavailable", ex);
        }
    }

    /** Outcome of the first factor: either a challenge, or a completed login. */
    public record LoginResult(boolean mfaRequired, UUID challengeId, TokenPair tokens) {

        static LoginResult mfaRequired(UUID challengeId) {
            return new LoginResult(true, challengeId, null);
        }

        static LoginResult authenticated(TokenPair tokens) {
            return new LoginResult(false, null, tokens);
        }
    }

    /** A freshly issued access and refresh token pair. */
    public record TokenPair(String accessToken, Instant accessTokenExpiresAt,
                            String refreshToken, Instant refreshTokenExpiresAt, UUID sessionId) {
    }
}
