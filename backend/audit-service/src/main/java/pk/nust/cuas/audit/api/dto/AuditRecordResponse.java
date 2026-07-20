package pk.nust.cuas.audit.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;
import pk.nust.cuas.audit.domain.model.AuditRecord;

/**
 * An audit record as returned to an auditor.
 *
 * <p>Hashes are rendered as lowercase hex rather than base64 so they can be compared by eye
 * against a verification report without decoding.
 */
@Schema(description = "One entry in the tamper-evident audit log")
public record AuditRecordResponse(
        @Schema(description = "Position in the chain", example = "1042") Long seq,
        @Schema(description = "Idempotency key") UUID auditId,
        @Schema(description = "When the action happened, at the source") Instant occurredAt,
        @Schema(description = "When this service durably recorded it") Instant recordedAt,
        @Schema(description = "User id, service name, or 'system'") String actorId,
        @Schema(description = "USER | SERVICE | SYSTEM") String actorType,
        @Schema(description = "Originating address") String actorIp,
        @Schema(description = "Originating session") String sessionId,
        @Schema(description = "What happened", example = "LOGIN_SUCCEEDED") String action,
        @Schema(description = "Kind of thing acted on", example = "USER") String resourceType,
        @Schema(description = "Specific thing acted on") String resourceId,
        @Schema(description = "SUCCESS | FAILURE | DENIED") String outcome,
        @Schema(description = "Structured detail; before/after diffs") Map<String, String> details,
        @Schema(description = "End-to-end trace identifier") String correlationId,
        @Schema(description = "Preceding record hash, hex") String prevHash,
        @Schema(description = "This record's hash, hex") String recordHash) {

    /**
     * @param record the domain record
     * @return its API representation
     */
    public static AuditRecordResponse from(AuditRecord record) {
        return new AuditRecordResponse(
                record.seq(),
                record.auditId(),
                record.occurredAt(),
                record.recordedAt(),
                record.actorId(),
                record.actorType().name(),
                record.actorIp(),
                record.sessionId(),
                record.action(),
                record.resourceType(),
                record.resourceId(),
                record.outcome().name(),
                record.details(),
                record.correlationId(),
                hex(record.prevHash()),
                hex(record.recordHash()));
    }

    private static String hex(byte[] value) {
        return value == null ? null : HexFormat.of().formatHex(value);
    }
}
