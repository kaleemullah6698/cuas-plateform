package pk.nust.cuas.auth.config;

import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import pk.nust.cuas.commons.audit.ActorType;
import pk.nust.cuas.commons.audit.AuditEvent;
import pk.nust.cuas.commons.audit.AuditOutcome;
import pk.nust.cuas.commons.audit.AuditPublisher;
import pk.nust.cuas.commons.bootstrap.BootstrapAdminProperties;

/**
 * Creates the first administrator's <em>credential</em> (ARCHITECTURE.md sections 4.1.2, 7.3; added
 * at M11).
 *
 * <p>The other half of the bootstrap: user-service creates the identity and grants ADMIN in its own
 * database, and this creates the password in {@code authdb}. There is no credential-creation
 * endpoint anywhere in the platform — auth-service authenticates, it does not register — so before
 * M11 a fresh deployment had no way in at all.
 *
 * <p><strong>The password is hashed with the service's own configured Argon2id encoder</strong>,
 * not a separately-configured one. A bootstrap that hashed with different parameters would produce
 * a credential the login path could still verify but would immediately want to re-hash, and the
 * mismatch would only surface as a puzzling first login.
 *
 * <p><strong>It never overwrites.</strong> If the credential exists the runner does nothing — it
 * does not reset the password to the configured one. Otherwise anybody able to restart a container,
 * or to set an environment variable on one, could take the administrator account: the restart would
 * quietly install a password they chose.
 */
@Component
@ConditionalOnProperty(name = "cuas.bootstrap.admin.enabled", havingValue = "true")
@EnableConfigurationProperties(BootstrapAdminProperties.class)
public class BootstrapCredentialInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(BootstrapCredentialInitializer.class);

    /** Domain-specific action name, per {@code AuditActions}' convention. */
    private static final String CREDENTIAL_CREATED = "CREDENTIAL_CREATED";
    private static final String RESOURCE_TYPE = "CREDENTIAL";

    private final JdbcTemplate jdbc;
    private final BootstrapAdminProperties properties;
    private final PasswordEncoder passwordEncoder;
    private final AuditPublisher audit;

    public BootstrapCredentialInitializer(JdbcTemplate jdbc, BootstrapAdminProperties properties,
                                          PasswordEncoder passwordEncoder, AuditPublisher audit) {
        this.jdbc = jdbc;
        this.properties = properties;
        this.passwordEncoder = passwordEncoder;
        this.audit = audit;
        // Checked in the constructor so the context fails to start, rather than at run() where the
        // application would already be serving requests without a way to sign into it.
        properties.requireUsablePassword();
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        UUID userId = properties.userId();

        Integer existing = jdbc.queryForObject(
                "SELECT count(*) FROM credentials WHERE user_id = ? OR username = ?",
                Integer.class, userId, properties.username());
        if (existing != null && existing > 0) {
            log.info("Bootstrap credential for '{}' already exists; leaving it untouched",
                    properties.username());
            return;
        }

        audit.publish(AuditEvent.of(CREDENTIAL_CREATED, RESOURCE_TYPE)
                .actor("system:bootstrap", ActorType.SYSTEM)
                .resourceId(userId.toString())
                .outcome(AuditOutcome.SUCCESS)
                .details(Map.of(
                        "username", properties.username(),
                        "reason", "first-administrator bootstrap (M11)"))
                .build());

        jdbc.update("""
                INSERT INTO credentials (user_id, username, password_hash)
                VALUES (?, ?, ?)
                """, userId, properties.username(), passwordEncoder.encode(properties.password()));

        log.warn("Created the bootstrap credential for '{}'. Change this password after first "
                        + "sign-in and disable cuas.bootstrap.admin.enabled.",
                properties.username());
    }
}
