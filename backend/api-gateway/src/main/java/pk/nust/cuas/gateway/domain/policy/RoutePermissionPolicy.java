package pk.nust.cuas.gateway.domain.policy;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import pk.nust.cuas.commons.security.Permission;
import pk.nust.cuas.gateway.domain.model.RoutePattern;

/**
 * The gateway's permission pre-check table (ARCHITECTURE.md section 7.4).
 *
 * <p>Section 7.4 enforces permissions at three layers, and this is the first: a <em>fast
 * rejection</em>. It is not the authority. The authority is the {@code @PreAuthorize} annotation
 * on the target endpoint, which sees the request the gateway can only guess at.
 *
 * <p>Two consequences follow, and they are the whole design of this class:
 *
 * <ul>
 *   <li><strong>It may only refuse, never grant.</strong> Matching a rule here means the caller is
 *       rejected at the edge without a network hop. Matching nothing means the request is
 *       forwarded — still authenticated — for the owning service to judge. A route this table has
 *       not heard of is therefore <em>not</em> a route without authorisation.</li>
 *   <li><strong>It must never be stricter than the service.</strong> Demanding a permission here
 *       that the endpoint does not require would deny a legitimate caller, and the resulting 403
 *       would be invisible in the service's own logs.</li>
 * </ul>
 *
 * <p>The table mirrors the {@code @PreAuthorize} annotations of the services that exist. Each
 * milestone adds its own rows as it adds its endpoints; an absent row degrades to a forwarded
 * request, never to an unguarded one.
 */
public final class RoutePermissionPolicy {

    private final Map<RoutePattern, Permission> rules;

    /**
     * Builds the policy with the platform's standard table.
     */
    public RoutePermissionPolicy() {
        this(standardRules());
    }

    /**
     * Builds the policy with an explicit table.
     *
     * @param rules pattern to required permission, evaluated in iteration order
     */
    public RoutePermissionPolicy(Map<RoutePattern, Permission> rules) {
        this.rules = Map.copyOf(rules);
    }

    /**
     * Mirrors the {@code @PreAuthorize} annotations of every routed service.
     *
     * <p>Completed at M11, when finding G-4's remaining services were routed. Each row is copied
     * from the annotation on the endpoint it guards; where a service's own annotation is absent —
     * notification-service's self-scoped endpoints, which act only on the caller's own record and
     * which section 7.4 has no permission for — there is deliberately no row here either. A rule
     * this table invented would be a second, undocumented authorisation model, and it would refuse
     * callers the service itself would have served.
     */
    private static Map<RoutePattern, Permission> standardRules() {
        return orderedRules(List.of(
                // user-service — pk.nust.cuas.user.api.rest.UserController
                Map.entry(RoutePattern.of("/api/v1/users", "GET"), Permission.USER_READ),
                Map.entry(RoutePattern.of("/api/v1/users/*", "GET"), Permission.USER_READ),
                Map.entry(RoutePattern.of("/api/v1/users", "POST"), Permission.USER_WRITE),
                Map.entry(RoutePattern.of("/api/v1/users/*", "PATCH"), Permission.USER_WRITE),
                Map.entry(RoutePattern.of("/api/v1/users/*", "DELETE"), Permission.USER_WRITE),
                Map.entry(RoutePattern.of("/api/v1/users/*/roles", "POST"), Permission.ROLE_WRITE),
                Map.entry(RoutePattern.of("/api/v1/users/*/roles", "DELETE"), Permission.ROLE_WRITE),

                // audit-service — pk.nust.cuas.audit.api.rest.AuditController
                Map.entry(RoutePattern.of("/api/v1/audit", "GET"), Permission.AUDIT_READ),
                Map.entry(RoutePattern.of("/api/v1/audit/verify", "POST"), Permission.AUDIT_VERIFY),

                // track-correlation — pk.nust.cuas.track.api.rest.TrackController
                Map.entry(RoutePattern.of("/api/v1/tracks", "GET"), Permission.TRACK_READ),
                Map.entry(RoutePattern.of("/api/v1/tracks/*", "GET"), Permission.TRACK_READ),
                Map.entry(RoutePattern.of("/api/v1/tracks/*/history", "GET"), Permission.TRACK_READ),

                // threat-assessment — pk.nust.cuas.threat.api.rest.ThreatController.
                // A threat score is a fact about a track, so the reads carry track:read; the policy
                // surface carries policy:read / policy:write. The /threats/policies rows are listed
                // before the /threats/* ones only for readability — resolution is by specificity,
                // not by order.
                Map.entry(RoutePattern.of("/api/v1/threats/policies", "GET"), Permission.POLICY_READ),
                Map.entry(RoutePattern.of("/api/v1/threats/policies", "PUT"), Permission.POLICY_WRITE),
                Map.entry(RoutePattern.of("/api/v1/threats/evaluate", "POST"), Permission.POLICY_READ),
                Map.entry(RoutePattern.of("/api/v1/threats/*", "GET"), Permission.TRACK_READ),
                Map.entry(RoutePattern.of("/api/v1/threats/*/breakdown", "GET"), Permission.TRACK_READ),

                // alert-service — pk.nust.cuas.alert.api.rest.AlertController
                Map.entry(RoutePattern.of("/api/v1/alerts", "GET"), Permission.ALERT_READ),
                Map.entry(RoutePattern.of("/api/v1/alerts/*", "GET"), Permission.ALERT_READ),
                Map.entry(RoutePattern.of("/api/v1/alerts/*/acknowledge", "POST"),
                        Permission.ALERT_ACKNOWLEDGE),
                Map.entry(RoutePattern.of("/api/v1/alerts/*/assign", "POST"),
                        Permission.ALERT_ACKNOWLEDGE),
                Map.entry(RoutePattern.of("/api/v1/alerts/*/resolve", "POST"),
                        Permission.ALERT_RESOLVE),

                // alert-service — pk.nust.cuas.alert.api.rest.IncidentController
                Map.entry(RoutePattern.of("/api/v1/incidents", "GET"), Permission.INCIDENT_READ),
                Map.entry(RoutePattern.of("/api/v1/incidents", "POST"), Permission.INCIDENT_CREATE),
                Map.entry(RoutePattern.of("/api/v1/incidents/*", "GET"), Permission.INCIDENT_READ),
                Map.entry(RoutePattern.of("/api/v1/incidents/*/timeline", "GET"),
                        Permission.INCIDENT_READ),
                Map.entry(RoutePattern.of("/api/v1/incidents/*/notes", "GET"),
                        Permission.INCIDENT_READ),
                Map.entry(RoutePattern.of("/api/v1/incidents/*/notes", "POST"),
                        Permission.INCIDENT_UPDATE),
                Map.entry(RoutePattern.of("/api/v1/incidents/*/assign", "POST"),
                        Permission.INCIDENT_UPDATE),
                Map.entry(RoutePattern.of("/api/v1/incidents/*/decision", "POST"),
                        Permission.INCIDENT_UPDATE),
                Map.entry(RoutePattern.of("/api/v1/incidents/*/close", "POST"),
                        Permission.INCIDENT_CLOSE),
                Map.entry(RoutePattern.of("/api/v1/incidents/*/evidence", "GET"),
                        Permission.EVIDENCE_EXPORT),
                Map.entry(RoutePattern.of("/api/v1/incidents/*/evidence", "POST"),
                        Permission.INCIDENT_UPDATE),

                // reporting-service — pk.nust.cuas.reporting.api.rest.ReportController
                Map.entry(RoutePattern.of("/api/v1/reports/templates", "GET"), Permission.REPORT_READ),
                Map.entry(RoutePattern.of("/api/v1/reports/generate", "POST"),
                        Permission.REPORT_GENERATE),
                Map.entry(RoutePattern.of("/api/v1/reports/jobs/*", "GET"), Permission.REPORT_READ),
                Map.entry(RoutePattern.of("/api/v1/reports/*/download", "GET"),
                        Permission.REPORT_READ),

                // config-service — pk.nust.cuas.config.api.rest.ConfigController. Reads carry
                // policy:read; a write is gated per namespace kind by the service itself
                // (policy:write / sensor:configure / ai:configure / config:write), which this table
                // cannot know from the path alone — so no write row, and the service decides.
                Map.entry(RoutePattern.of("/api/v1/config/*", "GET"), Permission.POLICY_READ),
                Map.entry(RoutePattern.of("/api/v1/config/*/history", "GET"), Permission.POLICY_READ),

                // ai-orchestrator — pk.nust.cuas.orchestrator.api.rest.AiServiceController
                Map.entry(RoutePattern.of("/api/v1/ai/services", "GET"), Permission.AI_CONFIGURE),
                Map.entry(RoutePattern.of("/api/v1/ai/services/*/model-version", "POST"),
                        Permission.AI_CONFIGURE)));
    }

    private static Map<RoutePattern, Permission> orderedRules(
            List<Map.Entry<RoutePattern, Permission>> entries) {
        var ordered = new java.util.LinkedHashMap<RoutePattern, Permission>();
        entries.forEach(entry -> ordered.put(entry.getKey(), entry.getValue()));
        return ordered;
    }

    /**
     * Returns the permission the caller must already hold to be worth forwarding.
     *
     * <p>More specific patterns are preferred over less specific ones, so that
     * {@code POST /api/v1/users/{id}/roles} resolves to {@code role:write} rather than to a
     * broader rule that also matches it. Specificity is measured by the number of literal
     * (non-wildcard) segments.
     *
     * @param method the request method
     * @param path   the request path, without query string
     * @return the required permission, or empty when this table has no opinion
     */
    public Optional<Permission> requiredPermission(String method, String path) {
        return rules.entrySet().stream()
                .filter(entry -> entry.getKey().matches(method, path))
                .max(java.util.Comparator.comparingInt(entry -> literalSegments(entry.getKey())))
                .map(Map.Entry::getValue);
    }

    private static int literalSegments(RoutePattern pattern) {
        return (int) java.util.Arrays.stream(pattern.pattern().split("/"))
                .filter(segment -> !segment.isEmpty())
                .filter(segment -> !segment.equals("*") && !segment.equals("**"))
                .count();
    }
}
