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
     * Mirrors the {@code @PreAuthorize} annotations of user-service (section 4.1.3) and
     * audit-service (section 4.1.11), the two services routed as of this milestone.
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
                Map.entry(RoutePattern.of("/api/v1/audit/verify", "POST"), Permission.AUDIT_VERIFY)));
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
