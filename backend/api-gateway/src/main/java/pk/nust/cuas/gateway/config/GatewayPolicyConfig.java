package pk.nust.cuas.gateway.config;

import java.time.Clock;
import java.util.List;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import pk.nust.cuas.gateway.api.filter.AccessLogFilter;
import pk.nust.cuas.gateway.common.DomainExceptionFilter;
import pk.nust.cuas.gateway.common.ProblemResponseWriter;
import pk.nust.cuas.gateway.domain.policy.ClientAddressPolicy;
import pk.nust.cuas.gateway.domain.policy.RateLimitPolicy;
import pk.nust.cuas.gateway.domain.policy.RoutePermissionPolicy;
import pk.nust.cuas.gateway.infrastructure.messaging.producer.GatewayEventPublisher;

/**
 * Wires the framework-free policy objects and the filters that sit outside the security chain.
 *
 * <p>The policies carry no Spring annotations of their own: they are plain objects that a unit
 * test can construct directly, and this class is the single place that knows they are also beans.
 */
@Configuration
public class GatewayPolicyConfig {

    /**
     * @return the platform clock; injected rather than read statically so tests can advance time
     */
    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }

    /** @return the section 7.8 rate-limit table */
    @Bean
    public RateLimitPolicy rateLimitPolicy() {
        return new RateLimitPolicy();
    }

    /** @return the section 7.4 gateway pre-check table */
    @Bean
    public RoutePermissionPolicy routePermissionPolicy() {
        return new RoutePermissionPolicy();
    }

    /**
     * @param trustedProxies addresses whose {@code X-Forwarded-For} may be believed; empty by
     *                       default, so an unconfigured deployment cannot be spoofed
     * @return the client-address policy
     */
    @Bean
    public ClientAddressPolicy clientAddressPolicy(
            @Value("${gateway.trusted-proxies:}") List<String> trustedProxies) {
        return new ClientAddressPolicy(Set.copyOf(trustedProxies));
    }

    /**
     * Registered outside the security chain and second only to the correlation-ID filter, so it
     * observes and records the outcome of every request — including those security rejects.
     *
     * @param eventPublisher publishes the sampled access stream
     * @param addressPolicy  resolves the client address
     * @param meterRegistry  request timers
     * @param clock          the platform clock
     * @param sampleRate     fraction of successful requests published to Kafka
     * @return the registration
     */
    @Bean
    public FilterRegistrationBean<AccessLogFilter> accessLogFilter(
            GatewayEventPublisher eventPublisher,
            ClientAddressPolicy addressPolicy,
            io.micrometer.core.instrument.MeterRegistry meterRegistry,
            Clock clock,
            @Value("${gateway.access-log.sample-rate:0.1}") double sampleRate) {
        var registration = new FilterRegistrationBean<>(
                new AccessLogFilter(eventPublisher, addressPolicy, meterRegistry, clock, sampleRate));
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 1);
        registration.addUrlPatterns("/*");
        registration.setName("accessLogFilter");
        return registration;
    }

    /**
     * Registered immediately inside the access log, so a fail-closed 503 is still logged and
     * counted rather than escaping to the container's error page.
     *
     * @param problemWriter renders the problem
     * @return the registration
     */
    @Bean
    public FilterRegistrationBean<DomainExceptionFilter> domainExceptionFilter(
            ProblemResponseWriter problemWriter) {
        var registration = new FilterRegistrationBean<>(new DomainExceptionFilter(problemWriter));
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 2);
        registration.addUrlPatterns("/*");
        registration.setName("domainExceptionFilter");
        return registration;
    }
}
