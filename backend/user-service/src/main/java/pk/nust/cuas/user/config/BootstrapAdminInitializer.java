package pk.nust.cuas.user.config;

import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import pk.nust.cuas.commons.audit.ActorType;
import pk.nust.cuas.commons.audit.AuditActions;
import pk.nust.cuas.commons.audit.AuditEvent;
import pk.nust.cuas.commons.audit.AuditOutcome;
import pk.nust.cuas.commons.audit.AuditPublisher;
import pk.nust.cuas.commons.bootstrap.BootstrapAdminProperties;
import pk.nust.cuas.commons.security.Role;

/**
 * Creates the first administrator's <em>identity</em> and grants it ADMIN (ARCHITECTURE.md sections
 * 4.1.3, 7.4; added at M11).
 *
 * <p>This is one half of the bootstrap. auth-service creates the matching credential in its own
 * database; the two agree because both bind the same {@link BootstrapAdminProperties} and are given
 * the same {@code userId}. Neither reaches into the other's database, so neither can create a
 * complete login on its own — and a deployment that configures only one gets a user who cannot sign
 * in, or a credential with no roles, rather than a silently half-made administrator.
 *
 * <p>Runs after {@link RbacReferenceDataInitializer}: {@code user_roles.role_code} is a foreign key
 * into {@code roles}, so ADMIN has to exist before it can be granted. That ordering is what
 * {@code @Order} is for here — without it the grant fails on a fresh database, intermittently,
 * depending on runner discovery order.
 *
 * <p><strong>Idempotent, and it never re-grants.</strong> If the user already exists the runner
 * makes no change at all. In particular it does not re-apply the ADMIN role: an administrator whose
 * roles were deliberately reduced must not have them restored by a restart.
 */
@Component
@Order(BootstrapAdminInitializer.ORDER)
@ConditionalOnProperty(name = "cuas.bootstrap.admin.enabled", havingValue = "true")
@EnableConfigurationProperties(BootstrapAdminProperties.class)
public class BootstrapAdminInitializer implements ApplicationRunner {

    /** After the RBAC reference data, which runs at the default order. */
    static final int ORDER = 100;

    private static final Logger log = LoggerFactory.getLogger(BootstrapAdminInitializer.class);
    private static final String RESOURCE_TYPE = "USER";

    private final JdbcTemplate jdbc;
    private final BootstrapAdminProperties properties;
    private final AuditPublisher audit;

    public BootstrapAdminInitializer(JdbcTemplate jdbc, BootstrapAdminProperties properties,
                                     AuditPublisher audit) {
        this.jdbc = jdbc;
        this.properties = properties;
        this.audit = audit;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        UUID userId = properties.userId();

        Integer existing = jdbc.queryForObject(
                "SELECT count(*) FROM users WHERE id = ? OR username = ?",
                Integer.class, userId, properties.username());
        if (existing != null && existing > 0) {
            log.info("Bootstrap administrator '{}' already exists; leaving it untouched",
                    properties.username());
            return;
        }

        // Audited before the insert commits (section 7.5, fail-closed). Creating an ADMIN is the
        // most privileged mutation the platform performs, and one that cannot be recorded must not
        // happen — least of all this one, which by definition has no authenticated actor behind it.
        audit.publish(AuditEvent.of(AuditActions.USER_CREATED, RESOURCE_TYPE)
                .actor("system:bootstrap", ActorType.SYSTEM)
                .resourceId(userId.toString())
                .outcome(AuditOutcome.SUCCESS)
                .details(Map.of(
                        "username", properties.username(),
                        "role", Role.ADMIN.name(),
                        "reason", "first-administrator bootstrap (M11)"))
                .build());

        jdbc.update("""
                INSERT INTO users (id, username, email, display_name, status)
                VALUES (?, ?, ?, ?, 'ACTIVE')
                """, userId, properties.username(), properties.email(), properties.displayName());

        jdbc.update("INSERT INTO user_roles (user_id, role_code) VALUES (?, ?)",
                userId, Role.ADMIN.name());

        log.warn("Created bootstrap administrator '{}' with the ADMIN role. Change its password "
                        + "after first sign-in and disable cuas.bootstrap.admin.enabled.",
                properties.username());
    }
}
