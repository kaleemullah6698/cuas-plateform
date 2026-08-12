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
        assertThat(policy.requiredPermission("GET", "/api/v1/nothing-here")).isEmpty();
        assertThat(policy.requiredPermission("POST", "/api/v1/tracks")).isEmpty();
    }

    @Test
    void aMethodWithoutARuleIsNotCoveredByARuleForAnotherMethod() {
        // PUT on the collection is not POST on the collection.
        assertThat(policy.requiredPermission("PUT", "/api/v1/users")).isEmpty();
    }

    // ── M11: the routes finding G-4 left unrouted ───────────────────────────────────────────────

    @Test
    void trackAndThreatReadsRequireTrackRead() {
        // A threat score is a fact about a track, so the service guards it with track:read rather
        // than inventing a threat:read the section 7.4 matrix does not contain.
        assertThat(policy.requiredPermission("GET", "/api/v1/tracks")).contains(Permission.TRACK_READ);
        assertThat(policy.requiredPermission("GET", "/api/v1/tracks/42"))
                .contains(Permission.TRACK_READ);
        assertThat(policy.requiredPermission("GET", "/api/v1/tracks/42/history"))
                .contains(Permission.TRACK_READ);
        assertThat(policy.requiredPermission("GET", "/api/v1/threats/42"))
                .contains(Permission.TRACK_READ);
        assertThat(policy.requiredPermission("GET", "/api/v1/threats/42/breakdown"))
                .contains(Permission.TRACK_READ);
    }

    @Test
    void theScoringPolicySurfaceBeatsTheThreatReadRule() {
        // "/threats/policies" also matches "/threats/*". The more specific literal must win, or
        // publishing a scoring policy would be pre-checked as a track read — which every role
        // holds, so the edge would forward a request only ADMIN may make.
        assertThat(policy.requiredPermission("GET", "/api/v1/threats/policies"))
                .contains(Permission.POLICY_READ);
        assertThat(policy.requiredPermission("PUT", "/api/v1/threats/policies"))
                .contains(Permission.POLICY_WRITE);
        assertThat(policy.requiredPermission("POST", "/api/v1/threats/evaluate"))
                .contains(Permission.POLICY_READ);
    }

    @Test
    void theAlertLifecycleMirrorsItsServiceAnnotations() {
        assertThat(policy.requiredPermission("GET", "/api/v1/alerts")).contains(Permission.ALERT_READ);
        assertThat(policy.requiredPermission("POST", "/api/v1/alerts/1/acknowledge"))
                .contains(Permission.ALERT_ACKNOWLEDGE);
        assertThat(policy.requiredPermission("POST", "/api/v1/alerts/1/assign"))
                .contains(Permission.ALERT_ACKNOWLEDGE);
        assertThat(policy.requiredPermission("POST", "/api/v1/alerts/1/resolve"))
                .contains(Permission.ALERT_RESOLVE);
    }

    @Test
    void closingAnIncidentRequiresIncidentCloseRatherThanIncidentUpdate() {
        // Section 7.4 grants incident:close to ANALYST and ADMIN only, not to OPERATOR. Resolving
        // this to the broader incident:update would forward a request an operator cannot make.
        assertThat(policy.requiredPermission("POST", "/api/v1/incidents/1/close"))
                .contains(Permission.INCIDENT_CLOSE);
        assertThat(policy.requiredPermission("POST", "/api/v1/incidents/1/notes"))
                .contains(Permission.INCIDENT_UPDATE);
    }

    @Test
    void readingAnEvidenceBundleRequiresEvidenceExport() {
        // Narrower than incident:read: an evidence bundle leaves the platform.
        assertThat(policy.requiredPermission("GET", "/api/v1/incidents/1/evidence"))
                .contains(Permission.EVIDENCE_EXPORT);
        // Attaching one is an ordinary update to the investigation.
        assertThat(policy.requiredPermission("POST", "/api/v1/incidents/1/evidence"))
                .contains(Permission.INCIDENT_UPDATE);
    }

    @Test
    void generatingAReportIsSeparateFromReadingOne() {
        assertThat(policy.requiredPermission("GET", "/api/v1/reports/templates"))
                .contains(Permission.REPORT_READ);
        assertThat(policy.requiredPermission("POST", "/api/v1/reports/generate"))
                .contains(Permission.REPORT_GENERATE);
        assertThat(policy.requiredPermission("GET", "/api/v1/reports/7/download"))
                .contains(Permission.REPORT_READ);
    }

    @Test
    void aConfigWriteIsLeftToTheServiceBecauseThePathDoesNotRevealTheNamespaceKind() {
        // config-service gates a write per namespace kind — policy:write, sensor:configure,
        // ai:configure or config:write — which the gateway cannot know from the path. A rule here
        // would have to guess, and guessing too strictly denies a legitimate caller with a 403 that
        // never appears in the service's own logs.
        assertThat(policy.requiredPermission("GET", "/api/v1/config/threat-policy"))
                .contains(Permission.POLICY_READ);
        assertThat(policy.requiredPermission("PUT", "/api/v1/config/threat-policy")).isEmpty();
    }

    @Test
    void theAiAdminSurfaceRequiresAiConfigure() {
        assertThat(policy.requiredPermission("GET", "/api/v1/ai/services"))
                .contains(Permission.AI_CONFIGURE);
        assertThat(policy.requiredPermission("POST", "/api/v1/ai/services/some-id/model-version"))
                .contains(Permission.AI_CONFIGURE);
    }

    @Test
    void notificationSelfServiceHasNoRuleBecauseSection74HasNoPermissionForIt() {
        // The endpoints act only on the caller's own preferences and history, and the service gates
        // on authentication alone. A rule invented here would be a second, undocumented
        // authorisation model — and it would refuse callers the service itself would serve.
        assertThat(policy.requiredPermission("GET", "/api/v1/notifications/preferences")).isEmpty();
        assertThat(policy.requiredPermission("PUT", "/api/v1/notifications/preferences")).isEmpty();
        assertThat(policy.requiredPermission("GET", "/api/v1/notifications/history")).isEmpty();
        assertThat(policy.requiredPermission("POST", "/api/v1/notifications/test")).isEmpty();
    }
}
