package pk.nust.cuas.user.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import pk.nust.cuas.commons.security.Permission;
import pk.nust.cuas.commons.security.Role;

/** The User aggregate: identity, role membership, and what those roles actually confer. */
class UserTest {

    private static User newUser() {
        return User.create("k.ullah", "k@nust.edu.pk", "Kaleem Ullah");
    }

    @Test
    void newAccountStartsActiveWithNoRoles() {
        User user = newUser();

        assertThat(user.isActive()).isTrue();
        assertThat(user.roles()).isEmpty();
        // No authority by default: roles are granted through the checked assignment path.
        assertThat(user.effectivePermissions()).isEmpty();
    }

    @Test
    void effectivePermissionsAreTheUnionOfAssignedRoles() {
        User user = newUser().withRole(Role.OPERATOR).withRole(Role.ENGINEER);

        assertThat(user.effectivePermissions())
                .containsAll(Role.OPERATOR.permissions())
                .containsAll(Role.ENGINEER.permissions());
    }

    @Test
    void deactivatedAccountHoldsNothingEvenWithRoles() {
        User deactivated = newUser().withRole(Role.ADMIN).deactivated();

        assertThat(deactivated.status()).isEqualTo(UserStatus.DEACTIVATED);
        assertThat(deactivated.holds(Permission.USER_WRITE)).isFalse();
        // Roles are retained so reactivation restores the previous authority rather than nothing.
        assertThat(deactivated.roles()).containsExactly(Role.ADMIN);
        assertThat(deactivated.reactivated().holds(Permission.USER_WRITE)).isTrue();
    }

    @Test
    void mutationReturnsNewInstanceLeavingTheOriginalUntouched() {
        User original = newUser();
        User withRole = original.withRole(Role.VIEWER);

        assertThat(original.roles()).isEmpty();
        assertThat(withRole.roles()).containsExactly(Role.VIEWER);
    }

    @Test
    void roleSetIsNotModifiableThroughTheAccessor() {
        User user = newUser().withRole(Role.VIEWER);

        assertThatThrownBy(() -> user.roles().add(Role.ADMIN))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void withoutRoleRemovesIt() {
        User user = newUser().withRole(Role.VIEWER).withRole(Role.OPERATOR);

        assertThat(user.withoutRole(Role.VIEWER).roles()).containsExactly(Role.OPERATOR);
    }

    @Test
    void blankRequiredFieldsAreRejected() {
        assertThatThrownBy(() -> User.create("  ", "e@x.pk", "Name"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> User.create("u", "", "Name"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void toStringDoesNotLeakEmail() {
        // User identifiers reach logs; personal data should not follow them there.
        assertThat(newUser().toString()).doesNotContain("k@nust.edu.pk");
    }

    @Test
    void identityIsByIdNotByFieldValues() {
        User user = newUser();

        assertThat(user).isEqualTo(user.withRole(Role.ADMIN));
        assertThat(user).isNotEqualTo(newUser());
    }
}
