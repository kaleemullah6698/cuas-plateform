package pk.nust.cuas.gateway.domain.policy;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import pk.nust.cuas.commons.security.Permission;

/**
 * The pre-check table of section 7.4, and the two properties that make it safe.
 */
class RoutePermissionPolicyTest {

    private final RoutePermissionPolicy policy = new RoutePermissionPolicy();

    @Test
    void readingUsersRequiresUserRead() {
        assertThat(policy.requiredPermission("GET", "/api/v1/users"))
                .contains(Permission.USER_READ);
        assertThat(policy.requiredPermission("GET", "/api/v1/users/42"))
                .contains(Permission.USER_READ);
    }

    @Test
    void writingUsersRequiresUserWrite() {
        assertThat(policy.requiredPermission("POST", "/api/v1/users"))
                .contains(Permission.USER_WRITE);
        assertThat(policy.requiredPermission("PATCH", "/api/v1/users/42"))
                .contains(Permission.USER_WRITE);
        assertThat(policy.requiredPermission("DELETE", "/api/v1/users/42"))
                .contains(Permission.USER_WRITE);
    }

    @Test
    void grantingARoleRequiresRoleWriteRatherThanUserWrite() {
        // The more specific rule must win. Resolving this to user:write would let anyone who can
        // edit a profile also hand out ADMIN.
        assertThat(policy.requiredPermission("POST", "/api/v1/users/42/roles"))
                .contains(Permission.ROLE_WRITE);
        assertThat(policy.requiredPermission("DELETE", "/api/v1/users/42/roles"))
                .contains(Permission.ROLE_WRITE);
    }

    @Test
    void auditRoutesMirrorTheirServiceAnnotations() {
        assertThat(policy.requiredPermission("GET", "/api/v1/audit"))
                .contains(Permission.AUDIT_READ);
        assertThat(policy.requiredPermission("POST", "/api/v1/audit/verify"))
                .contains(Permission.AUDIT_VERIFY);
    }

    @Test
    void anUnknownRouteHasNoOpinionAndIsForwardedForTheServiceToJudge() {
        // The pre-check may refuse but never grant: an absent rule is not an unguarded route,
        // it is one the owning service authorises on its own.
        assertThat(policy.requiredPermission("GET", "/api/v1/tracks")).isEmpty();
        assertThat(policy.requiredPermission("POST", "/api/v1/alerts/1/acknowledge")).isEmpty();
    }

    @Test
    void aMethodWithoutARuleIsNotCoveredByARuleForAnotherMethod() {
        // PUT on the collection is not POST on the collection.
        assertThat(policy.requiredPermission("PUT", "/api/v1/users")).isEmpty();
    }
}
