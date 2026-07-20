package pk.nust.cuas.gateway.api.filter;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.security.authentication.AuthenticationTrustResolver;
import org.springframework.security.authentication.AuthenticationTrustResolverImpl;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import pk.nust.cuas.commons.exception.ProblemDetailFactory;
import pk.nust.cuas.commons.security.Permission;
import pk.nust.cuas.gateway.common.ProblemResponseWriter;
import pk.nust.cuas.gateway.domain.policy.RoutePermissionPolicy;

/**
 * The first of the three authorisation layers of ARCHITECTURE.md section 7.4: a fast rejection.
 *
 * <p>The token already carries the caller's resolved {@code perms} (section 7.3), so the gateway
 * can tell that a VIEWER's request to create a user will be refused without spending a network
 * hop, a database connection and a service thread to find out.
 *
 * <p>It is a pre-check, never the decision. A route the policy has no rule for is forwarded, and
 * the owning service's {@code @PreAuthorize} remains authoritative — that is where a rule this
 * layer cannot express, such as ownership of the row being edited, is applied.
 */
@Component
public class PermissionPreCheckFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(PermissionPreCheckFilter.class);

    private final RoutePermissionPolicy policy;
    private final ProblemResponseWriter problemWriter;
    private final MeterRegistry meterRegistry;
    private final AuthenticationTrustResolver trustResolver = new AuthenticationTrustResolverImpl();

    public PermissionPreCheckFilter(RoutePermissionPolicy policy,
                                    ProblemResponseWriter problemWriter,
                                    MeterRegistry meterRegistry) {
        this.policy = policy;
        this.problemWriter = problemWriter;
        this.meterRegistry = meterRegistry;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        Optional<Permission> required =
                policy.requiredPermission(request.getMethod(), request.getRequestURI());

        if (required.isPresent() && !holds(required.get())) {
            Permission permission = required.get();
            Counter.builder("cuas.gateway.authorization.denied")
                    .description("Requests refused by the section 7.4 gateway pre-check")
                    .tag("permission", permission.authority())
                    .register(meterRegistry)
                    .increment();
            log.warn("Pre-check denied {} {}: caller lacks '{}'",
                    request.getMethod(), request.getRequestURI(), permission.authority());
            problemWriter.write(response, forbidden(permission));
            return;
        }

        filterChain.doFilter(request, response);
    }

    private boolean holds(Permission permission) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || trustResolver.isAnonymous(authentication)) {
            // An anonymous caller is passed through to the authorisation filter, which answers 401.
            // Note that an AnonymousAuthenticationToken reports isAuthenticated() as true, so
            // testing that flag would send every unauthenticated caller a 403 instead — telling
            // them the route exists and that authenticating would not have helped.
            return true;
        }
        return authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(permission.authority()::equals);
    }

    private ProblemDetail forbidden(Permission permission) {
        ProblemDetail problem = ProblemDetailFactory.create(
                HttpStatus.FORBIDDEN, "permission-denied",
                "This action requires a permission you do not hold.");
        problem.setProperty("requiredPermission", permission.authority());
        return problem;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return request.getRequestURI().startsWith("/actuator/");
    }
}
