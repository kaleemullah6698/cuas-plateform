package pk.nust.cuas.user.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import pk.nust.cuas.commons.audit.AuditPublisher;
import pk.nust.cuas.commons.security.Permission;
import pk.nust.cuas.commons.security.Role;
import pk.nust.cuas.user.application.UserApplicationService;
import pk.nust.cuas.user.domain.UserRepository;
import pk.nust.cuas.user.domain.exception.SelfEscalationDeniedException;
import pk.nust.cuas.user.domain.exception.UsernameAlreadyExistsException;
import pk.nust.cuas.user.domain.model.User;
import pk.nust.cuas.user.infrastructure.messaging.UserEventPublisher;

/**
 * Persistence and authorisation behaviour against a real PostgreSQL instance.
 *
 * <p>Testcontainers rather than an in-memory database because the guarantees under test are
 * PostgreSQL-specific: the Flyway migration that ships, the unique constraints, the element
 * collection mapping onto {@code user_roles}, and the rules that make {@code role_change_log}
 * immutable.
 *
 * <p>The audit and event publishers are mocked here so the test needs no broker; their fail-closed
 * behaviour is asserted separately in {@link UserApplicationServiceAuditTest}.
 */
@SpringBootTest
@Testcontainers
class UserRepositoryIntegrationTest {

    @Container
    @SuppressWarnings("resource")
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine")
            .withDatabaseName("userdb").withUsername("cuas").withPassword("test");

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @MockitoBean
    private AuditPublisher auditPublisher;

    @MockitoBean
    private UserEventPublisher userEventPublisher;

    @Autowired
    private UserRepository repository;

    @Autowired
    private UserApplicationService users;

    @Autowired
    private DataSource dataSource;

    private JdbcTemplate jdbc;

    @BeforeEach
    void reset() {
        jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("TRUNCATE user_roles, users CASCADE");
        jdbc.execute("TRUNCATE role_change_log RESTART IDENTITY");
    }

    private UserApplicationService.Actor admin() {
        return new UserApplicationService.Actor(UUID.randomUUID(), Role.ADMIN.permissions());
    }

    @Test
    void referenceDataIsSynchronisedFromTheEnums() {
        // The startup initializer must have populated the tables the enums define.
        Integer roles = jdbc.queryForObject("SELECT count(*) FROM roles", Integer.class);
        Integer permissions = jdbc.queryForObject("SELECT count(*) FROM permissions", Integer.class);

        assertThat(roles).isEqualTo(Role.values().length);
        assertThat(permissions).isEqualTo(Permission.values().length);
    }

    @Test
    void rolePermissionsTableMatchesTheMatrix() {
        Integer adminGrants = jdbc.queryForObject(
                "SELECT count(*) FROM role_permissions WHERE role_code = 'ADMIN'", Integer.class);

        assertThat(adminGrants).isEqualTo(Role.ADMIN.permissions().size());
        // ADMIN must not hold audit:export, in the database as well as in code.
        Integer adminExport = jdbc.queryForObject("""
                SELECT count(*) FROM role_permissions
                WHERE role_code = 'ADMIN' AND permission_code = 'audit:export'
                """, Integer.class);
        assertThat(adminExport).isZero();
    }

    @Test
    void userIsPersistedAndReloadedWithRoles() {
        User created = users.createUser("k.ullah", "k@nust.edu.pk", "Kaleem", admin());
        users.assignRole(created.id(), Role.OPERATOR, "Assigned to the night watch", admin());

        User reloaded = repository.findById(created.id()).orElseThrow();

        assertThat(reloaded.username()).isEqualTo("k.ullah");
        assertThat(reloaded.roles()).containsExactly(Role.OPERATOR);
        assertThat(reloaded.effectivePermissions()).containsAll(Role.OPERATOR.permissions());
    }

    @Test
    void duplicateUsernameIsRejected() {
        users.createUser("dup", "a@nust.edu.pk", "A", admin());

        assertThatThrownBy(() -> users.createUser("dup", "b@nust.edu.pk", "B", admin()))
                .isInstanceOf(UsernameAlreadyExistsException.class);
    }

    @Test
    void duplicateEmailIsRejected() {
        users.createUser("one", "same@nust.edu.pk", "A", admin());

        assertThatThrownBy(() -> users.createUser("two", "same@nust.edu.pk", "B", admin()))
                .isInstanceOf(UsernameAlreadyExistsException.class);
    }

    @Test
    void roleChangeIsRecordedWithItsJustification() {
        User created = users.createUser("k2", "k2@nust.edu.pk", "K2", admin());
        users.assignRole(created.id(), Role.VIEWER, "Read-only access for the audit review", admin());

        String justification = jdbc.queryForObject(
                "SELECT justification FROM role_change_log WHERE user_id = ?", String.class,
                created.id());
        assertThat(justification).isEqualTo("Read-only access for the audit review");
    }

    @Test
    void roleChangeLogCannotBeRewritten() {
        User created = users.createUser("k3", "k3@nust.edu.pk", "K3", admin());
        users.assignRole(created.id(), Role.VIEWER, "Initial read-only grant", admin());

        jdbc.update("UPDATE role_change_log SET justification = 'rewritten'");
        jdbc.update("DELETE FROM role_change_log");

        // History of a privilege change is not editable, enforced by database rules.
        assertThat(jdbc.queryForObject("SELECT justification FROM role_change_log", String.class))
                .isEqualTo("Initial read-only grant");
    }

    @Test
    void selfEscalationIsRefusedAndNothingIsPersisted() {
        User created = users.createUser("k4", "k4@nust.edu.pk", "K4", admin());
        var weakActor = new UserApplicationService.Actor(
                UUID.randomUUID(), Role.VIEWER.permissions());

        assertThatThrownBy(() -> users.assignRole(created.id(), Role.ADMIN, "trying it on", weakActor))
                .isInstanceOf(SelfEscalationDeniedException.class);

        assertThat(repository.findById(created.id()).orElseThrow().roles()).isEmpty();
        // A refused attempt must not leave a role-change record claiming it happened.
        assertThat(jdbc.queryForObject("SELECT count(*) FROM role_change_log", Integer.class))
                .isZero();
    }

    @Test
    void deactivationStripsEffectiveAuthorityButKeepsRoles() {
        User created = users.createUser("k5", "k5@nust.edu.pk", "K5", admin());
        users.assignRole(created.id(), Role.OPERATOR, "Operator for the evening shift", admin());

        users.deactivate(created.id(), admin());
        User reloaded = repository.findById(created.id()).orElseThrow();

        assertThat(reloaded.isActive()).isFalse();
        assertThat(reloaded.roles()).containsExactly(Role.OPERATOR);
        assertThat(reloaded.holds(Permission.ALERT_ACKNOWLEDGE)).isFalse();
    }

    @Test
    void revokingARoleNarrowsAuthority() {
        User created = users.createUser("k6", "k6@nust.edu.pk", "K6", admin());
        users.assignRole(created.id(), Role.OPERATOR, "Operator for the evening shift", admin());

        users.revokeRole(created.id(), Role.OPERATOR, "Shift ended, access withdrawn", admin());

        assertThat(repository.findById(created.id()).orElseThrow().roles()).isEmpty();
    }
}
