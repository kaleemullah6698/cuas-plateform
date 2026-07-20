package pk.nust.cuas.audit.infrastructure.messaging.consumer;

import com.google.protobuf.InvalidProtocolBufferException;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import pk.nust.cuas.audit.domain.model.AuditRecord;
import pk.nust.cuas.audit.domain.service.AuditAppendService;
import pk.nust.cuas.commons.audit.ActorType;
import pk.nust.cuas.commons.audit.AuditOutcome;

/**
 * Consumes {@code platform.audit.v1} and appends each event to the chain (ARCHITECTURE.md 4.1.11).
 *
 * <p>Audit is a pure consumer: it is fed by events and calls no other service, so it can never
 * become an availability dependency of the platform (section 4.2).
 *
 * <p>Concurrency is deliberately left at one consumer thread. Appending is serialised by an
 * advisory lock anyway, so additional threads would contend on that lock without adding
 * throughput, while making the ordering harder to reason about.
 *
 * <p>A malformed payload is acknowledged and logged rather than retried forever: it can never
 * become well-formed, so redelivery would stall the partition. Anything that might succeed on
 * retry is rethrown so the container's error handler routes it to the DLQ.
 */
@Component
public class AuditEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(AuditEventConsumer.class);

    private final AuditAppendService appendService;

    public AuditEventConsumer(AuditAppendService appendService) {
        this.appendService = appendService;
    }

    @KafkaListener(
            topics = AuditTopics.PLATFORM_AUDIT,
            groupId = "${spring.kafka.consumer.group-id:audit-service}",
            concurrency = "1")
    @Transactional
    public void onAuditEvent(byte[] payload) {
        pk.nust.cuas.audit.v1.AuditEvent event;
        try {
            event = pk.nust.cuas.audit.v1.AuditEvent.parseFrom(payload);
        } catch (InvalidProtocolBufferException ex) {
            log.error("Discarding malformed audit event ({} bytes): it cannot become valid on retry",
                    payload.length, ex);
            return;
        }

        AuditRecord record = toRecord(event);
        appendService.append(record).ifPresentOrElse(
                appended -> log.debug("Appended audit record seq={} action={} actor={}",
                        appended.seq(), appended.action(), appended.actorId()),
                () -> log.debug("Audit event {} already recorded; duplicate ignored",
                        event.getAuditId()));
    }

    private AuditRecord toRecord(pk.nust.cuas.audit.v1.AuditEvent event) {
        return new AuditRecord(
                null,
                UUID.fromString(event.getAuditId()),
                Instant.ofEpochSecond(event.getOccurredAt().getSeconds(),
                        event.getOccurredAt().getNanos()),
                null,
                event.getActorId(),
                actorType(event.getActorType()),
                emptyToNull(event.getActorIp()),
                emptyToNull(event.getSessionId()),
                event.getAction(),
                event.getResourceType(),
                emptyToNull(event.getResourceId()),
                outcome(event.getOutcome()),
                Map.copyOf(event.getDetailsMap()),
                emptyToNull(event.getCorrelationId()),
                null,
                null);
    }

    /** Proto3 cannot express null, so an absent optional string arrives as "". */
    private String emptyToNull(String value) {
        return value == null || value.isEmpty() ? null : value;
    }

    private ActorType actorType(pk.nust.cuas.audit.v1.ActorType type) {
        return switch (type) {
            case ACTOR_TYPE_USER -> ActorType.USER;
            case ACTOR_TYPE_SERVICE -> ActorType.SERVICE;
            // An unrecognised or unset actor type is recorded as SYSTEM rather than rejected:
            // losing the record entirely would be worse than recording it conservatively.
            default -> ActorType.SYSTEM;
        };
    }

    private AuditOutcome outcome(pk.nust.cuas.audit.v1.AuditOutcome outcome) {
        return switch (outcome) {
            case AUDIT_OUTCOME_SUCCESS -> AuditOutcome.SUCCESS;
            case AUDIT_OUTCOME_DENIED -> AuditOutcome.DENIED;
            default -> AuditOutcome.FAILURE;
        };
    }
}
