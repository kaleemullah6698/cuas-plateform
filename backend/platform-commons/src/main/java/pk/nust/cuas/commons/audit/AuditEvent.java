package pk.nust.cuas.commons.audit;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * A security-relevant action to be recorded in the tamper-evident log (ARCHITECTURE.md 7.5).
 *
 * <p>This is the publisher-side value object: framework-free, immutable, and validated at
 * construction, so an ill-formed event cannot be created let alone published. The chain fields
 * ({@code prev_hash}, {@code record_hash}) are absent by design — they are computed by
 * audit-service when the record is appended. A publisher able to set them could forge the chain.
 *
 * @param auditId       idempotency key; a duplicate is rejected at append, so at-least-once
 *                      delivery cannot produce a duplicate chain entry
 * @param occurredAt    when the action happened, at the source
 * @param actorId       user id, service name, or {@code "system"}
 * @param actorType     the kind of actor
 * @param actorIp       originating address, or {@code null} when not applicable
 * @param sessionId     originating session, or {@code null}
 * @param action        what happened, for example {@link AuditActions#LOGIN_SUCCEEDED}
 * @param resourceType  the kind of thing acted on, for example {@code USER}
 * @param resourceId    the specific thing acted on, or {@code null}
 * @param outcome       how the action ended
 * @param details       structured detail; before/after diffs for configuration changes
 * @param correlationId end-to-end trace identifier, or {@code null}
 */
public record AuditEvent(
        UUID auditId,
        Instant occurredAt,
        String actorId,
        ActorType actorType,
        String actorIp,
        String sessionId,
        String action,
        String resourceType,
        String resourceId,
        AuditOutcome outcome,
        Map<String, String> details,
        String correlationId) {

    public AuditEvent {
        Objects.requireNonNull(auditId, "auditId");
        Objects.requireNonNull(occurredAt, "occurredAt");
        Objects.requireNonNull(actorType, "actorType");
        Objects.requireNonNull(outcome, "outcome");
        requireText(actorId, "actorId");
        requireText(action, "action");
        requireText(resourceType, "resourceType");
        details = details == null ? Map.of() : Map.copyOf(details);
    }

    private static void requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be null or blank");
        }
    }

    /**
     * Starts a builder for an event that happened now, with a freshly generated audit id.
     *
     * @param action       what happened
     * @param resourceType the kind of thing acted on
     * @return a builder
     */
    public static Builder of(String action, String resourceType) {
        return new Builder(action, resourceType);
    }

    /** Fluent builder; the required fields are supplied to {@link AuditEvent#of}. */
    public static final class Builder {
        private final String action;
        private final String resourceType;
        private UUID auditId = UUID.randomUUID();
        private Instant occurredAt = Instant.now();
        private String actorId = "system";
        private ActorType actorType = ActorType.SYSTEM;
        private String actorIp;
        private String sessionId;
        private String resourceId;
        private AuditOutcome outcome = AuditOutcome.SUCCESS;
        private Map<String, String> details = Map.of();
        private String correlationId;

        private Builder(String action, String resourceType) {
            this.action = action;
            this.resourceType = resourceType;
        }

        public Builder auditId(UUID value) { this.auditId = value; return this; }
        public Builder occurredAt(Instant value) { this.occurredAt = value; return this; }
        public Builder actor(String id, ActorType type) { this.actorId = id; this.actorType = type; return this; }
        public Builder actorIp(String value) { this.actorIp = value; return this; }
        public Builder sessionId(String value) { this.sessionId = value; return this; }
        public Builder resourceId(String value) { this.resourceId = value; return this; }
        public Builder outcome(AuditOutcome value) { this.outcome = value; return this; }
        public Builder details(Map<String, String> value) { this.details = value; return this; }
        public Builder correlationId(String value) { this.correlationId = value; return this; }

        /** @return the immutable event */
        public AuditEvent build() {
            return new AuditEvent(auditId, occurredAt, actorId, actorType, actorIp, sessionId,
                    action, resourceType, resourceId, outcome, details, correlationId);
        }
    }
}
