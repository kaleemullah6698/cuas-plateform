package pk.nust.cuas.audit.infrastructure.persistence.mapper;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import org.springframework.stereotype.Component;
import pk.nust.cuas.audit.domain.model.AuditRecord;
import pk.nust.cuas.audit.domain.model.CanonicalJson;
import pk.nust.cuas.audit.infrastructure.persistence.entity.AuditLogEntity;
import pk.nust.cuas.commons.audit.ActorType;
import pk.nust.cuas.commons.audit.AuditOutcome;

/**
 * Translates between the domain {@link AuditRecord} and its persistence shape.
 *
 * <p>Details are written in canonical form so the stored JSON matches what was hashed. They are
 * read back into a map and re-canonicalised during verification, so PostgreSQL's own {@code JSONB}
 * normalisation (which may reorder keys) cannot cause a false tamper alarm.
 */
@Component
public class AuditRecordMapper {

    private static final TypeReference<Map<String, String>> STRING_MAP = new TypeReference<>() { };

    private final ObjectMapper objectMapper;

    public AuditRecordMapper(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * @param record the domain record
     * @return a new entity ready for insert
     */
    public AuditLogEntity toEntity(AuditRecord record) {
        AuditLogEntity entity = new AuditLogEntity();
        entity.setAuditId(record.auditId());
        entity.setOccurredAt(record.occurredAt());
        entity.setActorId(record.actorId());
        entity.setActorType(record.actorType().name());
        entity.setActorIp(record.actorIp());
        entity.setSessionId(record.sessionId());
        entity.setAction(record.action());
        entity.setResourceType(record.resourceType());
        entity.setResourceId(record.resourceId());
        entity.setOutcome(record.outcome().name());
        entity.setDetails(CanonicalJson.of(record.details()));
        entity.setCorrelationId(record.correlationId());
        entity.setPrevHash(record.prevHash());
        entity.setRecordHash(record.recordHash());
        return entity;
    }

    /**
     * @param entity the persisted row
     * @return the domain record
     */
    public AuditRecord toDomain(AuditLogEntity entity) {
        return new AuditRecord(
                entity.getSeq(),
                entity.getAuditId(),
                entity.getOccurredAt(),
                entity.getRecordedAt(),
                entity.getActorId(),
                ActorType.valueOf(entity.getActorType()),
                entity.getActorIp(),
                entity.getSessionId(),
                entity.getAction(),
                entity.getResourceType(),
                entity.getResourceId(),
                AuditOutcome.valueOf(entity.getOutcome()),
                readDetails(entity.getDetails()),
                entity.getCorrelationId(),
                entity.getPrevHash(),
                entity.getRecordHash());
    }

    private Map<String, String> readDetails(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(json, STRING_MAP);
        } catch (Exception ex) {
            throw new IllegalStateException("Audit details are not a readable JSON object", ex);
        }
    }
}
