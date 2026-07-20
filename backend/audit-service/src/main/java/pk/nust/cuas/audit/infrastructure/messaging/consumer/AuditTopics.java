package pk.nust.cuas.audit.infrastructure.messaging.consumer;

/**
 * Kafka topics this service consumes (ARCHITECTURE.md section 5.2).
 */
public final class AuditTopics {

    /** Every service publishes security-relevant actions here; audit is the sole consumer. */
    public static final String PLATFORM_AUDIT = "platform.audit.v1";

    private AuditTopics() {
    }
}
