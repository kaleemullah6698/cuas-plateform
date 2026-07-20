package pk.nust.cuas.gateway.api.filter;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ThreadLocalRandom;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.AuthenticationTrustResolver;
import org.springframework.security.authentication.AuthenticationTrustResolverImpl;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;
import pk.nust.cuas.commons.correlation.CorrelationId;
import pk.nust.cuas.gateway.domain.policy.ClientAddressPolicy;
import pk.nust.cuas.gateway.infrastructure.messaging.producer.GatewayEventPublisher;
import pk.nust.cuas.gateway.v1.GatewayAccess;

/**
 * Access logging and the sampled {@code platform.gateway.access.v1} stream (sections 4.1.1, 5.2).
 *
 * <p>Registered outside the security filter chain and ahead of it, so it observes the outcome of
 * every request including the ones security rejects. A log that only records requests which got
 * past authentication would omit precisely the traffic worth looking at.
 *
 * <p>Every request is logged locally; only a sampled fraction is published to Kafka, as section
 * 4.1.1 specifies. A rejected request is always published regardless of the sample rate, because
 * the reason to sample is volume and rejections are both rare and interesting.
 *
 * <p>The path is logged, the query string is not: filters and identifiers routinely end up in
 * query strings, and an access log is not an appropriate place for them.
 */
public class AccessLogFilter extends OncePerRequestFilter {

    private static final AuthenticationTrustResolver TRUST_RESOLVER =
            new AuthenticationTrustResolverImpl();
    private static final Logger log = LoggerFactory.getLogger(AccessLogFilter.class);

    private final GatewayEventPublisher eventPublisher;
    private final ClientAddressPolicy addressPolicy;
    private final MeterRegistry meterRegistry;
    private final Clock clock;
    private final double sampleRate;

    public AccessLogFilter(GatewayEventPublisher eventPublisher,
                           ClientAddressPolicy addressPolicy,
                           MeterRegistry meterRegistry,
                           Clock clock,
                           double sampleRate) {
        this.eventPublisher = eventPublisher;
        this.addressPolicy = addressPolicy;
        this.meterRegistry = meterRegistry;
        this.clock = clock;
        if (sampleRate < 0.0d || sampleRate > 1.0d) {
            throw new IllegalArgumentException(
                    "gateway.access-log.sample-rate must be between 0 and 1, was " + sampleRate);
        }
        this.sampleRate = sampleRate;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        Instant start = clock.instant();
        String method = request.getMethod();
        String path = request.getRequestURI();

        log.debug("--> {} {}", method, path);
        try {
            filterChain.doFilter(request, response);
        } finally {
            Duration elapsed = Duration.between(start, clock.instant());
            int status = response.getStatus();
            String userId = authenticatedSubject();

            if (status >= 500) {
                log.error("<-- {} {} {} in {} ms", method, path, status, elapsed.toMillis());
            } else if (status >= 400) {
                log.warn("<-- {} {} {} in {} ms", method, path, status, elapsed.toMillis());
            } else {
                log.debug("<-- {} {} {} in {} ms", method, path, status, elapsed.toMillis());
            }

            Timer.builder("cuas.gateway.request")
                    .description("Requests handled at the gateway")
                    .tag("method", method)
                    .tag("outcome", outcomeOf(status))
                    .register(meterRegistry)
                    .record(elapsed);

            if (shouldPublish(status)) {
                eventPublisher.publishAccess(GatewayAccess.newBuilder()
                        .setCorrelationId(CorrelationId.current().orElse(""))
                        .setUserId(userId == null ? "" : userId)
                        .setMethod(method)
                        .setPath(path)
                        .setStatusCode(status)
                        .setDurationMillis(elapsed.toMillis())
                        .setClientIp(addressPolicy.clientAddress(
                                request.getRemoteAddr(), request.getHeader("X-Forwarded-For")))
                        .setOccurredAt(GatewayEventPublisher.toTimestamp(start))
                        .build());
            }
        }
    }

    private boolean shouldPublish(int status) {
        return status >= 400 || ThreadLocalRandom.current().nextDouble() < sampleRate;
    }

    private static String outcomeOf(int status) {
        if (status >= 500) {
            return "server-error";
        }
        if (status == 429) {
            return "rate-limited";
        }
        if (status == 401 || status == 403) {
            return "denied";
        }
        if (status >= 400) {
            return "client-error";
        }
        return "success";
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

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        // Probes and scrapes are high-volume, uninteresting, and would dominate the sample.
        return request.getRequestURI().startsWith("/actuator/");
    }
}
