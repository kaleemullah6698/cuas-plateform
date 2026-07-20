package pk.nust.cuas.user.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import pk.nust.cuas.commons.audit.AuditActions;
import pk.nust.cuas.commons.audit.AuditEvent;
import pk.nust.cuas.commons.audit.AuditOutcome;
import pk.nust.cuas.commons.audit.AuditPublisher;
import pk.nust.cuas.commons.audit.AuditWriteException;
import pk.nust.cuas.commons.security.Role;
import pk.nust.cuas.user.application.UserApplicationService;
import pk.nust.cuas.user.domain.UserRepository;
import pk.nust.cuas.user.domain.exception.SelfEscalationDeniedException;
import pk.nust.cuas.user.domain.model.User;
import pk.nust.cuas.user.infrastructure.messaging.UserEventPublisher;

/**
 * The fail-closed audit guarantee of section 7.5, asserted end to end against a real database.
 *
 * <p>The claim "an action that cannot be recorded is an action that does not happen" is only true
 * if a failed audit publish actually rolls the change back. That is a property of the transaction
 * boundary, not of any single class, so it is tested here rather than in a unit test.
 */
@SpringBootTest
@Testcontainers
class UserApplicationServiceAuditTest {

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
    private UserApplicationService users;

    @Autowired
    private UserRepository repository;

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
    void aFailedAuditWriteRollsBackTheUserCreation() {
        doThrow(new AuditWriteException("broker unavailable"))
                .when(auditPublisher).publish(any());

        assertThatThrownBy(() -> users.createUser("ghost", "g@nust.edu.pk", "Ghost", admin()))
                .isInstanceOf(AuditWriteException.class);

        // The account must not exist: it could not be recorded, so it did not happen.
        assertThat(jdbc.queryForObject("SELECT count(*) FROM users", Integer.class)).isZero();
    }

    @Test
    void aFailedAuditWriteRollsBackARoleGrant() {
        User created = users.createUser("r1", "r1@nust.edu.pk", "R1", admin());
        doThrow(new AuditWriteException("broker unavailable"))
                .when(auditPublisher).publish(any());

        assertThatThrownBy(() -> users.assignRole(created.id(), Role.OPERATOR,
                "Operator for the night watch", admin()))
                .isInstanceOf(AuditWriteException.class);

        assertThat(repository.findById(created.id()).orElseThrow().roles()).isEmpty();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM role_change_log", Integer.class))
                .isZero();
    }

    @Test
    void everyRoleGrantProducesAnAuditRecord() {
        User created = users.createUser("r2", "r2@nust.edu.pk", "R2", admin());
        users.assignRole(created.id(), Role.VIEWER, "Read-only for the review", admin());

        ArgumentCaptor<AuditEvent> captor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditPublisher, org.mockito.Mockito.atLeast(2)).publish(captor.capture());

        assertThat(captor.getAllValues())
                .anySatisfy(event -> {
                    assertThat(event.action()).isEqualTo(AuditActions.ROLE_ASSIGNED);
                    assertThat(event.outcome()).isEqualTo(AuditOutcome.SUCCESS);
                    assertThat(event.details()).containsEntry("role", "VIEWER");
                    assertThat(event.details()).containsKey("justification");
                });
    }

    @Test
    void arefusedEscalationIsAuditedAsDenied() {
        User created = users.createUser("r3", "r3@nust.edu.pk", "R3", admin());
        var weak = new UserApplicationService.Actor(UUID.randomUUID(), Role.VIEWER.permissions());

        assertThatThrownBy(() -> users.assignRole(created.id(), Role.ADMIN, "trying it on", weak))
                .isInstanceOf(SelfEscalationDeniedException.class);

        ArgumentCaptor<AuditEvent> captor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditPublisher, org.mockito.Mockito.atLeastOnce()).publish(captor.capture());

        // An attempted escalation is exactly what a reviewer needs to see; recording only
        // successful changes would hide it.
        assertThat(captor.getAllValues()).anySatisfy(event -> {
            assertThat(event.action()).isEqualTo(AuditActions.SELF_ESCALATION_DENIED);
            assertThat(event.outcome()).isEqualTo(AuditOutcome.DENIED);
        });
    }

    @Test
    void deactivationIsAnnouncedSoSessionsCanBeRevoked() {
        User created = users.createUser("r4", "r4@nust.edu.pk", "R4", admin());

        users.deactivate(created.id(), admin());

        verify(userEventPublisher).publishDeactivated(org.mockito.ArgumentMatchers.eq(created.id()),
                org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void aFailedDeactivationAnnouncementRollsBackTheDeactivation() {
        User created = users.createUser("r5", "r5@nust.edu.pk", "R5", admin());
        doThrow(new IllegalStateException("broker unavailable"))
                .when(userEventPublisher).publishDeactivated(any(), any());

        assertThatThrownBy(() -> users.deactivate(created.id(), admin()))
                .isInstanceOf(IllegalStateException.class);

        // Otherwise the account would look deactivated here while its sessions stayed alive.
        assertThat(repository.findById(created.id()).orElseThrow().isActive()).isTrue();
    }

    @Test
    void nothingIsAnnouncedWhenDeactivationFailsValidation() {
        assertThatThrownBy(() -> users.deactivate(UUID.randomUUID(), admin()));

        verify(userEventPublisher, never()).publishDeactivated(any(), any());
    }
}
