package pk.nust.cuas.gateway.domain.policy;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import pk.nust.cuas.gateway.domain.model.RateLimitKey;
import pk.nust.cuas.gateway.domain.model.RateLimitRule;
import pk.nust.cuas.gateway.domain.model.RoutePattern;

/**
 * The rate-limit table of ARCHITECTURE.md section 7.8, as buckets and the identities they key on.
 *
 * <p>Section 7.8 says to key by "the most specific applicable identity", and several rows apply at
 * once: a login attempt is limited both per source address and per account, so that neither a
 * single host hammering many accounts nor a distributed attempt against one account slips through.
 * {@link #bucketsFor} therefore returns every bucket a request must pay into, most specific first,
 * so that a request rejected by a narrow rule has not already spent a token from the broad one.
 *
 * <p>Pure and framework-free: it maps a described request onto buckets and knows nothing about
 * where the buckets live or how a rejection is rendered.
 *
 * <p>Two rows of the section 7.8 table are deliberately absent, and both are absent for the same
 * reason — the information they key on does not exist at the edge yet, and a rule that silently
 * matches nothing is worse than no rule:
 *
 * <ul>
 *   <li>{@code POST /ingest/detections} per sensor. The identity is a sensor id carried inside the
 *       payload; sensor-ingestion and its authentication arrive with M4, and the rule lands with
 *       them rather than guessing an identity now.</li>
 *   <li>WebSocket connections per user. That is a concurrency cap on live connections, not a token
 *       bucket, and it belongs to the WebSocket fan-out delivered in M8.</li>
 * </ul>
 */
public final class RateLimitPolicy {

    /** {@code POST /auth/login} per IP: 5/min, burst 5 (section 7.8). */
    public static final RateLimitRule LOGIN_PER_IP =
            new RateLimitRule("login-per-ip", 5, Duration.ofMinutes(1), 5, true);

    /** {@code POST /auth/login} per account: 10/hour, burst 3 (section 7.8). */
    public static final RateLimitRule LOGIN_PER_ACCOUNT =
            new RateLimitRule("login-per-account", 10, Duration.ofHours(1), 3, true);

    /** {@code POST /audit/export} per user: 5/hour, burst 1, security event raised (section 7.8). */
    public static final RateLimitRule AUDIT_EXPORT_PER_USER =
            new RateLimitRule("audit-export-per-user", 5, Duration.ofHours(1), 1, true);

    /** {@code POST /reports/generate} per user: 10/hour, burst 2 (section 7.8). */
    public static final RateLimitRule REPORT_GENERATE_PER_USER =
            new RateLimitRule("report-generate-per-user", 10, Duration.ofHours(1), 2, false);

    /** Authenticated API per user: 300/min, burst 50 (section 7.8). */
    public static final RateLimitRule AUTHENTICATED_API_PER_USER =
            new RateLimitRule("authenticated-api-per-user", 300, Duration.ofMinutes(1), 50, false);

    private static final RoutePattern LOGIN = RoutePattern.of("/api/v1/auth/login", "POST");
    private static final RoutePattern AUDIT_EXPORT = RoutePattern.of("/api/v1/audit/export", "POST");
    private static final RoutePattern REPORT_GENERATE =
            RoutePattern.of("/api/v1/reports/generate", "POST");

    /**
     * Every bucket the request must draw a token from, most specific first.
     *
     * @param request the request, described only by what the policy needs to see
     * @return the buckets in evaluation order; empty when nothing limits this request
     */
    public List<RateLimitKey> bucketsFor(LimitedRequest request) {
        List<RateLimitKey> buckets = new ArrayList<>();

        if (LOGIN.matches(request.method(), request.path())) {
            // The address is always known; the account is known only once the body names one.
            buckets.add(new RateLimitKey(LOGIN_PER_IP, request.clientIp()));
            if (hasText(request.accountUsername())) {
                buckets.add(new RateLimitKey(LOGIN_PER_ACCOUNT, request.accountUsername()));
            }
            return buckets;
        }

        if (!hasText(request.userId())) {
            // Anonymous traffic to anything but login has no per-user quota to draw on; it is
            // rejected by authentication rather than by a limit.
            return List.of();
        }

        if (AUDIT_EXPORT.matches(request.method(), request.path())) {
            buckets.add(new RateLimitKey(AUDIT_EXPORT_PER_USER, request.userId()));
        } else if (REPORT_GENERATE.matches(request.method(), request.path())) {
            buckets.add(new RateLimitKey(REPORT_GENERATE_PER_USER, request.userId()));
        }
        buckets.add(new RateLimitKey(AUTHENTICATED_API_PER_USER, request.userId()));
        return buckets;
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    /**
     * The parts of a request that determine which quotas it draws on.
     *
     * @param method          the HTTP method
     * @param path            the request path, without query string
     * @param clientIp        the originating address, after resolving any trusted proxy header
     * @param userId          the authenticated subject, or {@code null} when anonymous
     * @param accountUsername the account named by a login attempt, or {@code null} otherwise
     */
    public record LimitedRequest(
            String method,
            String path,
            String clientIp,
            String userId,
            String accountUsername) {
    }
}
