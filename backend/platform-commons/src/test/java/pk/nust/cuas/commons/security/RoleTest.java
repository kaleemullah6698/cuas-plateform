package pk.nust.cuas.commons.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/**
 * Guards the section 7.4 authorisation model. These assertions exist so that a future edit
 * widening a role fails the build rather than shipping.
 */
class RoleTest {

    @Test
    void adminMustNotHoldAuditExport() {
        // Separation of duty: whoever can change permissions must not be able to produce the
        // authoritative signed record of having done so.
        assertThat(ADMIN_HAS_AUDIT_EXPORT()).isFalse();
    }

    private boolean ADMIN_HAS_AUDIT_EXPORT() {
        return Role.ADMIN.grants(Permission.AUDIT_EXPORT);
    }

    @Test
    void auditorIsTheOnlyRoleThatCanExportTheAuditLog() {
        var exporters = Arrays.stream(Role.values())
                .filter(role -> role.grants(Permission.AUDIT_EXPORT))
                .toList();

        assertThat(exporters).containsExactly(Role.AUDITOR);
    }

    @Test
    void separationOfDutyAssertionPasses() {
        assertThatCode(Role::assertSeparationOfDuty).doesNotThrowAnyException();
    }

    @Test
    void onlyAuditorAndAdminCanReadTheAuditLog() {
        var readers = Arrays.stream(Role.values())
                .filter(role -> role.grants(Permission.AUDIT_READ))
                .toList();

        assertThat(readers).containsExactlyInAnyOrder(Role.AUDITOR, Role.ADMIN);
    }

    @Test
    void onlyAdminCanWriteUsersRolesAndPolicy() {
        assertThat(rolesGranting(Permission.USER_WRITE)).containsExactly(Role.ADMIN);
        assertThat(rolesGranting(Permission.ROLE_WRITE)).containsExactly(Role.ADMIN);
        assertThat(rolesGranting(Permission.POLICY_WRITE)).containsExactly(Role.ADMIN);
    }

    @Test
    void viewerIsStrictlyReadOnly() {
        assertThat(Role.VIEWER.permissions()).containsExactlyInAnyOrder(
                Permission.TRACK_READ, Permission.MAP_READ,
                Permission.ALERT_READ, Permission.SENSOR_READ);
    }

    @Test
    void engineerHasNoOperationalAuthorityOverAlerts() {
        assertThat(Role.ENGINEER.grants(Permission.ALERT_ACKNOWLEDGE)).isFalse();
        assertThat(Role.ENGINEER.grants(Permission.ALERT_RESOLVE)).isFalse();
        assertThat(Role.ENGINEER.grants(Permission.INCIDENT_CREATE)).isFalse();
        // but does own the sensing and AI infrastructure
        assertThat(Role.ENGINEER.grants(Permission.SENSOR_CONFIGURE)).isTrue();
        assertThat(Role.ENGINEER.grants(Permission.AI_CONFIGURE)).isTrue();
    }

    @Test
    void everyRoleCanReadTracksMapAlertsAndSensors() {
        for (Role role : Role.values()) {
            assertThat(role.grants(Permission.TRACK_READ)).as("%s track:read", role).isTrue();
            assertThat(role.grants(Permission.MAP_READ)).as("%s map:read", role).isTrue();
            assertThat(role.grants(Permission.ALERT_READ)).as("%s alert:read", role).isTrue();
            assertThat(role.grants(Permission.SENSOR_READ)).as("%s sensor:read", role).isTrue();
        }
    }

    @ParameterizedTest
    @EnumSource(Role.class)
    void permissionSetIsImmutable(Role role) {
        assertThat(role.permissions())
                .isUnmodifiable()
                .isNotEmpty();
    }

    @Test
    void everyPermissionResolvesFromItsAuthorityString() {
        for (Permission permission : Permission.values()) {
            assertThat(Permission.fromAuthority(permission.authority()))
                    .as(permission.name())
                    .contains(permission);
        }
    }

    @Test
    void unknownAuthorityDoesNotResolve() {
        assertThat(Permission.fromAuthority("audit:destroy")).isEmpty();
        assertThat(Permission.fromAuthority("")).isEmpty();
    }

    private java.util.List<Role> rolesGranting(Permission permission) {
        return Arrays.stream(Role.values()).filter(role -> role.grants(permission)).toList();
    }
}
