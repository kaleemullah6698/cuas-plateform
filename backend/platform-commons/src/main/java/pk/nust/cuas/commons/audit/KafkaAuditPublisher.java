package pk.nust.cuas.commons.audit;

import com.google.protobuf.Timestamp;
import java.time.Duration;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;

/**
 * Publishes audit events to the {@code platform.audit.v1} topic (ARCHITECTURE.md sections 5.2, 7.5).
 *
 * <p>The send is deliberately <em>synchronous</em>. Kafka's producer API is asynchronous by
 * default, which would let a caller believe an action was recorded when the broker never received
 * it; blocking on the result is what makes the fail-closed guarantee real. The publish timeout is
 * bounded so a broker outage surfaces as a fast, explicit failure rather than a hung request.
 *
 * <p>Events are keyed by {@code actorId} so that all activity for one actor lands on a single
 * partition and is therefore consumed in order.
 */
public class KafkaAuditPublisher implements AuditPublisher {

    /** Audit topic (section 5.2). */
    public static final String TOPIC = "platform.audit.v1";

    private static final Logger log = LoggerFactory.getLogger(KafkaAuditPublisher.class);

    private final KafkaTemplate<String, byte[]> kafkaTemplate;
    private final Duration publishTimeout;

    public KafkaAuditPublisher(KafkaTemplate<String, byte[]> kafkaTemplate, Duration publishTimeout) {
        this.kafkaTemplate = kafkaTemplate;
        this.publishTimeout = publishTimeout;
    }

    @Override
    public void publish(AuditEvent event) {
        byte[] payload = toProto(event).toByteArray();
        try {
            kafkaTemplate.send(TOPIC, event.actorId(), payload)
                    .get(publishTimeout.toMillis(), TimeUnit.MILLISECONDS);
            log.debug("Audit event recorded: action={} actor={} outcome={}",
                    event.action(), event.actorId(), event.outcome());
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new AuditWriteException("Interrupted while recording audit event " + event.auditId(), ex);
        } catch (ExecutionException | TimeoutException ex) {
            // Deliberately fatal to the caller: the action must not be treated as having happened.
            log.error("Audit event {} could not be recorded; aborting the audited action",
                    event.auditId(), ex);
            throw new AuditWriteException("Failed to record audit event " + event.auditId(), ex);
        }
    }

    private pk.nust.cuas.audit.v1.AuditEvent toProto(AuditEvent event) {
        var builder = pk.nust.cuas.audit.v1.AuditEvent.newBuilder()
                .setAuditId(event.auditId().toString())
                .setOccurredAt(Timestamp.newBuilder()
                        .setSeconds(event.occurredAt().getEpochSecond())
                        .setNanos(event.occurredAt().getNano())
                        .build())
                .setActorId(event.actorId())
                .setActorType(actorType(event.actorType()))
                .setAction(event.action())
                .setResourceType(event.resourceType())
                .setOutcome(outcome(event.outcome()))
                .putAllDetails(event.details());

        // Proto3 scalars cannot hold null; absent optional fields stay at their empty default.
        if (event.actorIp() != null) {
            builder.setActorIp(event.actorIp());
        }
        if (event.sessionId() != null) {
            builder.setSessionId(event.sessionId());
        }
        if (event.resourceId() != null) {
            builder.setResourceId(event.resourceId());
        }
        if (event.correlationId() != null) {
            builder.setCorrelationId(event.correlationId());
        }
        return builder.build();
    }

    private pk.nust.cuas.audit.v1.ActorType actorType(ActorType type) {
        return switch (type) {
            case USER -> pk.nust.cuas.audit.v1.ActorType.ACTOR_TYPE_USER;
            case SERVICE -> pk.nust.cuas.audit.v1.ActorType.ACTOR_TYPE_SERVICE;
            case SYSTEM -> pk.nust.cuas.audit.v1.ActorType.ACTOR_TYPE_SYSTEM;
        };
    }

    private pk.nust.cuas.audit.v1.AuditOutcome outcome(AuditOutcome outcome) {
        return switch (outcome) {
            case SUCCESS -> pk.nust.cuas.audit.v1.AuditOutcome.AUDIT_OUTCOME_SUCCESS;
            case FAILURE -> pk.nust.cuas.audit.v1.AuditOutcome.AUDIT_OUTCOME_FAILURE;
            case DENIED -> pk.nust.cuas.audit.v1.AuditOutcome.AUDIT_OUTCOME_DENIED;
        };
    }
}
