package pk.nust.cuas.gateway.api.filter;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.security.authentication.AuthenticationTrustResolver;
import org.springframework.security.authentication.AuthenticationTrustResolverImpl;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import pk.nust.cuas.commons.exception.ProblemDetailFactory;
import pk.nust.cuas.gateway.common.ProblemResponseWriter;
import pk.nust.cuas.gateway.domain.model.RateLimitDecision;
import pk.nust.cuas.gateway.domain.model.RateLimitKey;
import pk.nust.cuas.gateway.domain.policy.ClientAddressPolicy;
import pk.nust.cuas.gateway.domain.policy.RateLimitPolicy;
import pk.nust.cuas.gateway.domain.service.RateLimiter;
import pk.nust.cuas.gateway.infrastructure.messaging.producer.RateLimitRejectionRecorder;

/**
 * Enforces the token buckets of ARCHITECTURE.md section 7.8 at the edge.
 *
 * <p>Placed after bearer-token authentication in the security chain, so an authenticated request
 * is charged to its user rather than to its address, and a login attempt — which is permitted
 * through unauthenticated — is charged to both its address and the account it names.
 *
 * <p>Every response carries the {@code X-RateLimit-*} headers of section 7.8, not only the
 * refusals: a client that can watch its remaining quota can pace itself, whereas one that only
 * learns the limit by hitting it will keep hitting it.
 */
@Component
public class RateLimitFilter extends OncePerRequestFilter {

    private static final AuthenticationTrustResolver TRUST_RESOLVER =
            new AuthenticationTrustResolverImpl();
    private static final Logger log = LoggerFactory.getLogger(RateLimitFilter.class);
    private static final String LOGIN_PATH = "/api/v1/auth/login";
    private static final String USERNAME_FIELD = "username";

    private final RateLimitPolicy policy;
    private final RateLimiter rateLimiter;
    private final ClientAddressPolicy addressPolicy;
    private final RateLimitRejectionRecorder rejectionRecorder;
    private final ProblemResponseWriter problemWriter;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public RateLimitFilter(RateLimitPolicy policy,
                           RateLimiter rateLimiter,
                           ClientAddressPolicy addressPolicy,
                           RateLimitRejectionRecorder rejectionRecorder,
                           ProblemResponseWriter problemWriter,
                           ObjectMapper objectMapper,
                           Clock clock) {
        this.policy = policy;
        this.rateLimiter = rateLimiter;
        this.addressPolicy = addressPolicy;
        this.rejectionRecorder = rejectionRecorder;
        this.problemWriter = problemWriter;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String path = request.getRequestURI();
        String method = request.getMethod();
        String clientIp = addressPolicy.clientAddress(
                request.getRemoteAddr(), request.getHeader("X-Forwarded-For"));
        String userId = authenticatedSubject();

        HttpServletRequest forwarded = request;
        String accountUsername = null;
        if (LOGIN_PATH.equals(path) && "POST".equalsIgnoreCase(method)) {
            forwarded = BufferedBodyRequest.buffering(request);
            accountUsername = usernameFrom(forwarded);
        }

        var buckets = policy.bucketsFor(
                new RateLimitPolicy.LimitedRequest(method, path, clientIp, userId, accountUsername));

        Instant now = clock.instant();
        RateLimitDecision tightest = null;
        for (RateLimitKey bucket : buckets) {
            // An unreachable bucket store propagates as EdgeStateUnavailableException and is
            // rendered as 503 by DomainExceptionFilter: the request is refused, never waved
            // through unlimited.
            RateLimitDecision decision = rateLimiter.tryConsume(bucket);
            if (!decision.allowed()) {
                applyHeaders(response, decision, now);
                rejectionRecorder.record(bucket, decision, method, path, clientIp, userId, now);
                problemWriter.write(response, tooManyRequests(bucket, decision, now));
                return;
            }
            tightest = tighter(tightest, decision);
        }

        // A request may draw on several buckets, and only one set of headers can be returned. The
        // one with least room left is the useful answer: it is the quota the caller will meet
        // first, and reporting a roomier one would invite them to pace against a limit that is not
        // the binding one.
        if (tightest != null) {
            applyHeaders(response, tightest, now);
        }
        filterChain.doFilter(forwarded, response);
    }

    /**
     * The login body is JSON supplied by an unauthenticated caller, so a malformed one is
     * expected rather than exceptional: it simply yields no account to charge, and the per-address
     * limit still applies.
     */
    private String usernameFrom(HttpServletRequest request) {
        if (!(request instanceof BufferedBodyRequest buffered)) {
            return null;
        }
        try {
            JsonNode root = objectMapper.readTree(buffered.body());
            JsonNode username = root == null ? null : root.get(USERNAME_FIELD);
            return username != null && username.isTextual() ? username.asText() : null;
        } catch (IOException ex) {
            log.debug("Login body is not readable JSON; limiting by address only");
            return null;
        }
    }

    private String authenticatedSubject() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || TRUST_RESOLVER.isAnonymous(authentication)) {
            // An anonymous token reports isAuthenticated() as true, so the trust resolver is the
            // only reliable way to tell "nobody" from "someone".
            return null;
        }
        return authentication.getName();
    }

    private static RateLimitDecision tighter(RateLimitDecision current, RateLimitDecision candidate) {
        if (current == null || candidate.remaining() < current.remaining()) {
            return candidate;
        }
        return current;
    }

    private void applyHeaders(HttpServletResponse response, RateLimitDecision decision, Instant now) {
        response.setHeader("X-RateLimit-Limit", Long.toString(decision.limit()));
        response.setHeader("X-RateLimit-Remaining", Long.toString(decision.remaining()));
        response.setHeader("X-RateLimit-Reset", Long.toString(decision.resetAt().getEpochSecond()));
        if (!decision.allowed()) {
            response.setHeader("Retry-After", Long.toString(decision.retryAfterSeconds(now)));
        }
    }

    private ProblemDetail tooManyRequests(RateLimitKey bucket, RateLimitDecision decision,
                                          Instant now) {
        ProblemDetail problem = ProblemDetailFactory.create(
                HttpStatus.TOO_MANY_REQUESTS, "rate-limit-exceeded",
                "Too many requests. Retry after "
                        + decision.retryAfterSeconds(now) + " seconds.");
        // The rule name tells an operator which of the section 7.8 rows they met; the identity it
        // was keyed on is deliberately not echoed, since on the login path that would confirm
        // whether an account name exists.
        problem.setProperty("rule", bucket.rule().name());
        problem.setProperty("retryAfterSeconds", decision.retryAfterSeconds(now));
        return problem;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        // Liveness and readiness probes must not be able to exhaust anyone's quota, and must keep
        // answering when a quota is exhausted.
        return request.getRequestURI().startsWith("/actuator/");
    }
}
