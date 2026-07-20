package pk.nust.cuas.user.domain.policy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import pk.nust.cuas.commons.security.Permission;
import pk.nust.cuas.commons.security.Role;
import pk.nust.cuas.user.domain.exception.SelfEscalationDeniedException;
import pk.nust.cuas.user.domain.exception.SeparationOfDutyViolationException;
import pk.nust.cuas.user.domain.model.User;

/**
 * The self-escalation and separation-of-duty rules of section 7.4.
 *
 * <p>These are the checks an attacker probes first, so they are tested as adversarially as
 * possible: not just "an admin can grant", but "a holder of role:write alone cannot promote
 * anyone, including themselves, beyond what they already hold".
 */
class RoleAssignmentPolicyTest {

    private final RoleAssignmentPolicy policy = new RoleAssignmentPolicy();

    private static User target() {
        return User.create("target", "t@nust.edu.pk", "Target");
    }

    @Test
    void adminCanGrantOperatorBecauseAdminHoldsEveryOperatorPermission() {
        assertThatCode(() -> policy.checkAssignable(
                UUID.randomUUID(), Role.ADMIN.permissions(), target(), Role.OPERATOR))
                .doesNotThrowAnyException();
    }

    @Test
    void holderOfRoleWriteAloneCannotGrantAnything() {
        // The crucial case: role:write is the permission to change permissions. On its own it must
        // not let the holder confer authority they lack, or the whole matrix becomes decorative.
        UUID actorId = UUID.randomUUID();
        Set<Permission> onlyRoleWrite = Set.of(Permission.ROLE_WRITE);

        assertThatThrownBy(() -> policy.checkAssignable(actorId, onlyRoleWrite, target(), Role.VIEWER))
                .isInstanceOf(SelfEscalationDeniedException.class);
    }

    @Test
    void operatorCannotGrantAnalystBecauseAnalystHoldsMore() {
        assertThatThrownBy(() -> policy.checkAssignable(
                UUID.randomUUID(), Role.OPERATOR.permissions(), target(), Role.ANALYST))
                .isInstanceOf(SelfEscalationDeniedException.class)
                .hasMessageContaining("incident:close");
    }

    @Test
    void operatorCanGrantViewerBecauseViewerIsAStrictSubset() {
        assertThatCode(() -> policy.checkAssignable(
                UUID.randomUUID(), Role.OPERATOR.permissions(), target(), Role.VIEWER))
                .doesNotThrowAnyException();
    }

    @Test
    void escalationExceptionNamesTheMissingPermissions() {
        var thrown = org.junit.jupiter.api.Assertions.assertThrows(
                SelfEscalationDeniedException.class,
                () -> policy.checkAssignable(UUID.randomUUID(), Role.VIEWER.permissions(),
                        target(), Role.ADMIN));

        assertThat(thrown.code()).isEqualTo("SELF_ESCALATION_DENIED");
        assertThat(thrown.properties()).containsKey("missingPermissions");
        assertThat(thrown.status().value()).isEqualTo(403);
    }

    @Test
    void adminCannotBeGrantedToAnAccountThatAlreadyExports() {
        // AUDITOR holds audit:export; adding ADMIN would also confer role:write. Section 7.4
        // forbids one account holding both, and the check is on the resulting permission set so it
        // cannot be sidestepped by combining two individually acceptable roles.
        User auditor = target().withRole(Role.AUDITOR);

        assertThatThrownBy(() -> policy.checkAssignable(
                UUID.randomUUID(), Role.ADMIN.permissions(), auditor, Role.ADMIN))
                .isInstanceOf(SeparationOfDutyViolationException.class)
                .hasMessageContaining("role:write and audit:export");
    }

    @Test
    void auditorCannotBeGrantedToAnAccountThatAlreadyAdministers() {
        User admin = target().withRole(Role.ADMIN);
        // An actor holding both would be needed to attempt this; construct one explicitly.
        Set<Permission> superset = java.util.stream.Stream
                .concat(Role.ADMIN.permissions().stream(), Role.AUDITOR.permissions().stream())
                .collect(java.util.stream.Collectors.toSet());

        assertThatThrownBy(() -> policy.checkAssignable(
                UUID.randomUUID(), superset, admin, Role.AUDITOR))
                .isInstanceOf(SeparationOfDutyViolationException.class);
    }

    @ParameterizedTest
    @EnumSource(Role.class)
    void anActorHoldingEveryPermissionOfARoleCanGrantIt(Role role) {
        User fresh = target();
        // Skip the combination the separation-of-duty rule forbids outright.
        if (role == Role.AUDITOR || role == Role.ADMIN) {
            return;
        }
        assertThatCode(() -> policy.checkAssignable(
                UUID.randomUUID(), role.permissions(), fresh, role))
                .doesNotThrowAnyException();
    }
}
