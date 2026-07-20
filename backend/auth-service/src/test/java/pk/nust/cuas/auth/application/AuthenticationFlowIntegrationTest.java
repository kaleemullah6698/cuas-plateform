package pk.nust.cuas.auth.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

import com.nimbusds.jwt.SignedJWT;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import pk.nust.cuas.auth.domain.exception.AccountLockedException;
import pk.nust.cuas.auth.domain.exception.AuthenticationFailedException;
import pk.nust.cuas.auth.domain.exception.InvalidRefreshTokenException;
import pk.nust.cuas.auth.domain.exception.MfaVerificationFailedException;
import pk.nust.cuas.auth.domain.model.TotpGenerator;
import pk.nust.cuas.auth.infrastructure.user.PermissionResolver;
import pk.nust.cuas.commons.audit.AuditActions;
import pk.nust.cuas.commons.audit.AuditEvent;
import pk.nust.cuas.commons.audit.AuditOutcome;
import pk.nust.cuas.commons.audit.AuditPublisher;
import pk.nust.cuas.commons.audit.AuditWriteException;

/**
 * The authentication flows of section 7.3, against a real PostgreSQL instance.
 *
 * <p>Testcontainers rather than mocks because the properties under test are transactional and
 * schema-level: that a failed audit publish rolls back a login, that refresh-token rotation is
 * atomic, and that reuse detection revokes a whole family in one statement.
 *
 * <p>The permission resolver is stubbed: user-service is a separate deployable, and standing it up
 * here would test its wiring rather than these flows.
 */
@SpringBootTest
@Testcontainers
class AuthenticationFlowIntegrationTest {

    @Container
    @SuppressWarnings("resource")
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine")
            .withDatabaseName("authdb").withUsername("cuas").withPassword("test");

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    /** Stands in for user-service; every account resolves as an active OPERATOR. */
    @TestConfiguration
    static class StubResolverConfig {
        @Bean
        @Primary
        PermissionResolver stubPermissionResolver() {
            return userId -> new PermissionResolver.Resolution(
                    List.of("OPERATOR"),
                    List.of("track:read", "alert:read", "alert:acknowledge"),
                    true);
        }
    }

    @MockitoBean
    private AuditPublisher audit;

    @Autowired
    private AuthenticationService authentication;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private TotpGenerator totp;

    @Autowired
    private DataSource dataSource;

    private JdbcTemplate jdbc;
    private UUID userId;

    private static final String USERNAME = "k.ullah";
    private static final String PASSWORD = "correct-horse-battery-staple";

    @BeforeEach
    void seedCredential() {
        jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("TRUNCATE refresh_tokens, sessions, mfa_challenges, mfa_secrets, credentials CASCADE");
        jdbc.execute("TRUNCATE login_attempts RESTART IDENTITY");

        userId = UUID.randomUUID();
        jdbc.update("INSERT INTO credentials (user_id, username, password_hash) VALUES (?, ?, ?)",
                userId, USERNAME, passwordEncoder.encode(PASSWORD));
    }

    private void enableMfa(String secret) {
        jdbc.update("INSERT INTO mfa_secrets (user_id, secret, enabled) VALUES (?, ?, true)",
                userId, secret);
    }

    // ── Password authentication ──

    @Test
    void correctPasswordWithoutMfaIssuesTokens() {
        var result = authentication.login(USERNAME, PASSWORD);

        assertThat(result.mfaRequired()).isFalse();
        assertThat(result.tokens().accessToken()).isNotBlank();
        assertThat(result.tokens().refreshToken()).isNotBlank();
    }

    @Test
    void accessTokenIsSignedRs256AndCarriesResolvedPermissions() throws Exception {
        var tokens = authentication.login(USERNAME, PASSWORD).tokens();

        SignedJWT jwt = SignedJWT.parse(tokens.accessToken());
        assertThat(jwt.getHeader().getAlgorithm().getName()).isEqualTo("RS256");
        assertThat(jwt.getJWTClaimsSet().getSubject()).isEqualTo(userId.toString());
        // The permission set is embedded so downstream services never call back to resolve it.
        assertThat(jwt.getJWTClaimsSet().getStringListClaim("perms"))
                .contains("alert:acknowledge");
        assertThat(jwt.getJWTClaimsSet().getExpirationTime()).isAfter(new java.util.Date());
    }

    @Test
    void wrongPasswordIsRejected() {
        assertThatThrownBy(() -> authentication.login(USERNAME, "wrong"))
                .isInstanceOf(AuthenticationFailedException.class);
    }

    @Test
    void unknownUsernameFailsIdenticallyToAWrongPassword() {
        // Both must present the same code and message, or an unauthenticated caller could
        // enumerate valid usernames by comparing responses.
        var unknown = org.junit.jupiter.api.Assertions.assertThrows(
                AuthenticationFailedException.class,
                () -> authentication.login("no-such-user", PASSWORD));
        var wrongPassword = org.junit.jupiter.api.Assertions.assertThrows(
                AuthenticationFailedException.class,
                () -> authentication.login(USERNAME, "wrong"));

        assertThat(unknown.getMessage()).isEqualTo(wrongPassword.getMessage());
        assertThat(unknown.code()).isEqualTo(wrongPassword.code());
    }

    @Test
    void passwordIsStoredOnlyAsAnArgon2idHash() {
        String stored = jdbc.queryForObject(
                "SELECT password_hash FROM credentials WHERE user_id = ?", String.class, userId);

        assertThat(stored).startsWith("$argon2id$").doesNotContain(PASSWORD);
    }

    @Test
    void repeatedFailuresLockTheAccount() {
        for (int i = 0; i < 5; i++) {
            assertThatThrownBy(() -> authentication.login(USERNAME, "wrong"))
                    .isInstanceOf(AuthenticationFailedException.class);
        }

        // The sixth attempt is refused as locked even though the password is now correct.
        assertThatThrownBy(() -> authentication.login(USERNAME, PASSWORD))
                .isInstanceOf(AccountLockedException.class);
    }

    @Test
    void aSuccessfulLoginClearsTheFailureCounter() {
        assertThatThrownBy(() -> authentication.login(USERNAME, "wrong"));
        authentication.login(USERNAME, PASSWORD);

        Integer failed = jdbc.queryForObject(
                "SELECT failed_attempts FROM credentials WHERE user_id = ?", Integer.class, userId);
        assertThat(failed).isZero();
    }

    @Test
    void everyAttemptIsRecorded() {
        assertThatThrownBy(() -> authentication.login(USERNAME, "wrong"));
        authentication.login(USERNAME, PASSWORD);

        assertThat(jdbc.queryForObject("SELECT count(*) FROM login_attempts", Integer.class))
                .isEqualTo(2);
    }

    @Test
    void loginAttemptHistoryCannotBeRewritten() {
        assertThatThrownBy(() -> authentication.login(USERNAME, "wrong"));

        jdbc.update("UPDATE login_attempts SET succeeded = true");
        jdbc.update("DELETE FROM login_attempts");

        assertThat(jdbc.queryForObject(
                "SELECT succeeded FROM login_attempts", Boolean.class)).isFalse();
    }

    // ── Multi-factor ──

    @Test
    void mfaEnabledAccountGetsAChallengeInsteadOfTokens() {
        enableMfa(totp.generateSecret());

        var result = authentication.login(USERNAME, PASSWORD);

        assertThat(result.mfaRequired()).isTrue();
        assertThat(result.challengeId()).isNotNull();
        // Crucially, no tokens are issued until the second factor is cleared.
        assertThat(result.tokens()).isNull();
    }

    @Test
    void correctCodeCompletesTheLogin() {
        String secret = totp.generateSecret();
        enableMfa(secret);
        UUID challengeId = authentication.login(USERNAME, PASSWORD).challengeId();

        var tokens = authentication.verifyMfa(challengeId, totp.generate(secret, Instant.now()));

        assertThat(tokens.accessToken()).isNotBlank();
    }

    @Test
    void wrongCodeIsRejected() {
        enableMfa(totp.generateSecret());
        UUID challengeId = authentication.login(USERNAME, PASSWORD).challengeId();

        assertThatThrownBy(() -> authentication.verifyMfa(challengeId, "000000"))
                .isInstanceOf(MfaVerificationFailedException.class);
    }

    @Test
    void aChallengeCannotBeUsedTwice() {
        String secret = totp.generateSecret();
        enableMfa(secret);
        UUID challengeId = authentication.login(USERNAME, PASSWORD).challengeId();
        String code = totp.generate(secret, Instant.now());
        authentication.verifyMfa(challengeId, code);

        assertThatThrownBy(() -> authentication.verifyMfa(challengeId, code))
                .isInstanceOf(MfaVerificationFailedException.class);
    }

    @Test
    void aCodeCannotBeReplayedOnAFreshChallenge() {
        String secret = totp.generateSecret();
        enableMfa(secret);
        String code = totp.generate(secret, Instant.now());
        authentication.verifyMfa(authentication.login(USERNAME, PASSWORD).challengeId(), code);

        // New challenge, same still-valid code: the used time step blocks it.
        UUID second = authentication.login(USERNAME, PASSWORD).challengeId();
        assertThatThrownBy(() -> authentication.verifyMfa(second, code))
                .isInstanceOf(MfaVerificationFailedException.class);
    }

    // ── Refresh rotation and reuse detection ──

    @Test
    void refreshRotatesToANewTokenPair() {
        var first = authentication.login(USERNAME, PASSWORD).tokens();

        var rotated = authentication.refresh(first.refreshToken());

        assertThat(rotated.refreshToken()).isNotEqualTo(first.refreshToken());
        assertThat(rotated.sessionId()).isEqualTo(first.sessionId());
    }

    @Test
    void reusingAnExchangedTokenRevokesTheWholeFamily() {
        var first = authentication.login(USERNAME, PASSWORD).tokens();
        var second = authentication.refresh(first.refreshToken());

        // Presenting the retired token means it was captured; the login is no longer trustworthy.
        assertThatThrownBy(() -> authentication.refresh(first.refreshToken()))
                .isInstanceOf(InvalidRefreshTokenException.class);

        // The current token is revoked too — that is the point: the attacker is locked out, and so
        // is the legitimate holder, who must authenticate again.
        assertThatThrownBy(() -> authentication.refresh(second.refreshToken()))
                .isInstanceOf(InvalidRefreshTokenException.class);
    }

    @Test
    void reuseDetectionIsAudited() {
        var first = authentication.login(USERNAME, PASSWORD).tokens();
        authentication.refresh(first.refreshToken());
        assertThatThrownBy(() -> authentication.refresh(first.refreshToken()));

        ArgumentCaptor<AuditEvent> captor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(audit, org.mockito.Mockito.atLeastOnce()).publish(captor.capture());

        assertThat(captor.getAllValues()).anySatisfy(event -> {
            assertThat(event.action()).isEqualTo(AuditActions.TOKEN_REUSE_DETECTED);
            assertThat(event.outcome()).isEqualTo(AuditOutcome.DENIED);
        });
    }

    @Test
    void reuseDetectionAlsoRevokesTheSession() {
        var first = authentication.login(USERNAME, PASSWORD).tokens();
        authentication.refresh(first.refreshToken());
        assertThatThrownBy(() -> authentication.refresh(first.refreshToken()));

        Integer live = jdbc.queryForObject(
                "SELECT count(*) FROM sessions WHERE revoked_at IS NULL", Integer.class);
        assertThat(live).isZero();
    }

    @Test
    void refreshTokensAreStoredOnlyAsDigests() {
        var tokens = authentication.login(USERNAME, PASSWORD).tokens();

        Integer matching = jdbc.queryForObject(
                "SELECT count(*) FROM refresh_tokens WHERE token_hash = ?", Integer.class,
                tokens.refreshToken());
        // A database disclosure must not hand over usable tokens.
        assertThat(matching).isZero();
    }

    @Test
    void anUnknownRefreshTokenIsRejected() {
        assertThatThrownBy(() -> authentication.refresh("not-a-real-token"))
                .isInstanceOf(InvalidRefreshTokenException.class);
    }

    // ── Logout and revocation ──

    @Test
    void logoutRevokesTheFamilyAndSession() {
        var tokens = authentication.login(USERNAME, PASSWORD).tokens();

        authentication.logout(tokens.refreshToken());

        assertThatThrownBy(() -> authentication.refresh(tokens.refreshToken()))
                .isInstanceOf(InvalidRefreshTokenException.class);
    }

    @Test
    void revokeAllEndsEverySessionForTheUser() {
        var first = authentication.login(USERNAME, PASSWORD).tokens();
        var second = authentication.login(USERNAME, PASSWORD).tokens();

        authentication.revokeAllForUser(userId, "USER_DEACTIVATED");

        assertThatThrownBy(() -> authentication.refresh(first.refreshToken()));
        assertThatThrownBy(() -> authentication.refresh(second.refreshToken()));
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM sessions WHERE revoked_at IS NULL", Integer.class)).isZero();
    }

    // ── Fail-closed auditing ──

    @Test
    void aFailedAuditWriteRollsBackTheLogin() {
        doThrow(new AuditWriteException("broker unavailable")).when(audit).publish(any());

        assertThatThrownBy(() -> authentication.login(USERNAME, PASSWORD))
                .isInstanceOf(AuditWriteException.class);

        // No session may survive an authentication that could not be recorded.
        assertThat(jdbc.queryForObject("SELECT count(*) FROM sessions", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM refresh_tokens", Integer.class))
                .isZero();
    }

    @Test
    void successfulLoginIsAudited() {
        authentication.login(USERNAME, PASSWORD);

        ArgumentCaptor<AuditEvent> captor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(audit, org.mockito.Mockito.atLeastOnce()).publish(captor.capture());

        assertThat(captor.getAllValues()).anySatisfy(event ->
                assertThat(event.action()).isEqualTo(AuditActions.LOGIN_SUCCEEDED));
    }

    @Test
    void failedLoginIsAudited() {
        assertThatThrownBy(() -> authentication.login(USERNAME, "wrong"));

        ArgumentCaptor<AuditEvent> captor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(audit, org.mockito.Mockito.atLeastOnce()).publish(captor.capture());

        assertThat(captor.getAllValues()).anySatisfy(event -> {
            assertThat(event.action()).isEqualTo(AuditActions.LOGIN_FAILED);
            assertThat(event.outcome()).isEqualTo(AuditOutcome.FAILURE);
        });
    }
}
