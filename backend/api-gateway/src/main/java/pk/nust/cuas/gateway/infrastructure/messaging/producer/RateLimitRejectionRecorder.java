package pk.nust.cuas.gateway.infrastructure.messaging.producer;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import pk.nust.cuas.commons.audit.ActorType;
import pk.nust.cuas.commons.audit.AuditActions;
import pk.nust.cuas.commons.audit.AuditEvent;
import pk.nust.cuas.commons.audit.AuditOutcome;
import pk.nust.cuas.commons.audit.AuditPublisher;
import pk.nust.cuas.commons.audit.AuditWriteException;
import pk.nust.cuas.commons.correlation.CorrelationId;
import pk.nust.cuas.gateway.domain.model.RateLimitDecision;
import pk.nust.cuas.gateway.domain.model.RateLimitKey;
import pk.nust.cuas.gateway.v1.GatewayRateLimited;

/**
 * Records a rate-limit rejection everywhere section 7.8 requires it to be recorded.
 *
 * <p>Three destinations, three different guarantees, which is why this is one class rather than
 * three calls scattered through the filter:
 *
 * <ul>
 *   <li>a Micrometer counter, for the Security dashboard of section 8.6;</li>
 *   <li>{@code platform.gateway.ratelimit.v1}, best-effort, for Alert and Reporting;</li>
 *   <li>the tamper-evident audit log — but <em>only</em> for rules section 7.8 marks
 *       security-relevant, and there the write must succeed.</li>
 * </ul>
 *
 * <p>Refusing a request whose refusal could not be audited is still the right outcome: the caller
 * is turned away either way, and the audit failure is surfaced separately rather than being
 * allowed to convert a rejection into an admission.
 */
@Component
public class RateLimitRejectionRecorder {

    private static final Logger log = LoggerFactory.getLogger(RateLimitRejectionRecorder.class);

    private final AuditPublisher auditPublisher;
    private final GatewayEventPublisher eventPublisher;
    private final MeterRegistry meterRegistry;

    public RateLimitRejectionRecorder(AuditPublisher auditPublisher,
                                      GatewayEventPublisher eventPublisher,
                                      MeterRegistry meterRegistry) {
        this.auditPublisher = auditPublisher;
        this.eventPublisher = eventPublisher;
        this.meterRegistry = meterRegistry;
    }

    /**
     * @param key       the exhausted bucket
     * @param decision  the refusal, for its retry hint
     * @param method    the request method
     * @param path      the request path
     * @param clientIp  the originating address
     * @param userId    the authenticated subject, or {@code null} when anonymous
     * @param now       the instant of the rejection
     */
    public void record(RateLimitKey key, RateLimitDecision decision, String method, String path,
                       String clientIp, String userId, Instant now) {
        String rule = key.rule().name();
        Counter.builder("cuas.gateway.ratelimit.rejected")
                .description("Requests refused by a section 7.8 rate limit")
                .tag("rule", rule)
                .register(meterRegistry)
                .increment();

        log.warn("Rate limit '{}' exceeded for identity '{}' on {} {}",
                rule, key.identity(), method, path);

        eventPublisher.publishRateLimited(GatewayRateLimited.newBuilder()
                .setCorrelationId(CorrelationId.current().orElse(""))
                .setRule(rule)
                .setIdentity(key.identity())
                .setMethod(method)
                .setPath(path)
                .setClientIp(clientIp == null ? "" : clientIp)
                .setRetryAfterSeconds(decision.retryAfterSeconds(now))
                .setOccurredAt(GatewayEventPublisher.toTimestamp(now))
                .build());

        if (key.rule().securitySensitive()) {
            audit(key, method, path, clientIp, userId, now);
        }
    }

    private void audit(RateLimitKey key, String method, String path, String clientIp,
                       String userId, Instant now) {
        Map<String, String> details = new LinkedHashMap<>();
        details.put("rule", key.rule().name());
        details.put("identity", key.identity());
        details.put("method", method);
        details.put("path", path);

        AuditEvent event = AuditEvent.of(AuditActions.RATE_LIMIT_EXCEEDED, "GATEWAY_ROUTE")
                .occurredAt(now)
                .actor(userId == null || userId.isBlank() ? "anonymous" : userId,
                        userId == null || userId.isBlank() ? ActorType.SYSTEM : ActorType.USER)
                .actorIp(clientIp)
                .resourceId(method + " " + path)
                .outcome(AuditOutcome.DENIED)
                .details(details)
                .correlationId(CorrelationId.current().orElse(null))
                .build();

        try {
            auditPublisher.publish(event);
        } catch (AuditWriteException ex) {
            // The request is refused regardless; losing the record must not become a way to make
            // the refusal disappear, so it is logged at ERROR and the rejection still stands.
            log.error("Could not audit a rate-limit rejection under rule '{}': {}",
                    key.rule().name(), ex.getMessage(), ex);
        }
    }
}
