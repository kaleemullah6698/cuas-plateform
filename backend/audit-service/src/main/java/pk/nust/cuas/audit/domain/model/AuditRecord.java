package pk.nust.cuas.audit.domain.model;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import pk.nust.cuas.commons.audit.ActorType;
import pk.nust.cuas.commons.audit.AuditOutcome;

/**
 * One entry in the tamper-evident audit log (ARCHITECTURE.md section 7.5).
 *
 * <p>Immutable by construction and validated in the compact constructor, so an invalid record
 * cannot exist. {@code prevHash} and {@code recordHash} are supplied by {@link HashChain} at
 * append time rather than by the publisher — a caller able to choose its own chain links could
 * forge history.
 *
 * <p>{@code seq} is assigned by the database and is {@code null} on a record not yet appended.
 *
 * @param seq           database-assigned position in the chain, or {@code null} before append
 * @param auditId       idempotency key; a duplicate is rejected at append
 * @param occurredAt    when the action happened, at the source
 * @param recordedAt    when this service durably recorded it, or {@code null} before append
 * @param actorId       user id, service name, or {@code "system"}
 * @param actorType     the kind of actor
 * @param actorIp       originating address, or {@code null}
 * @param sessionId     originating session, or {@code null}
 * @param action        what happened
 * @param resourceType  the kind of thing acted on
 * @param resourceId    the specific thing acted on, or {@code null}
 * @param outcome       how the action ended
 * @param details       structured detail; hashed in canonical form
 * @param correlationId end-to-end trace identifier, or {@code null}
 * @param prevHash      {@code recordHash} of the preceding record; {@link HashChain#GENESIS_HASH} first
 * @param recordHash    digest over {@code prevHash} and this record's canonical content
 */
public record AuditRecord(
        Long seq,
        UUID auditId,
        Instant occurredAt,
        Instant recordedAt,
        String actorId,
        ActorType actorType,
        String actorIp,
        String sessionId,
        String action,
        String resourceType,
        String resourceId,
        AuditOutcome outcome,
        Map<String, String> details,
        String correlationId,
        byte[] prevHash,
        byte[] recordHash) {

    public AuditRecord {
        Objects.requireNonNull(auditId, "auditId");
        Objects.requireNonNull(occurredAt, "occurredAt");
        Objects.requireNonNull(actorType, "actorType");
        Objects.requireNonNull(outcome, "outcome");
        requireText(actorId, "actorId");
        requireText(action, "action");
        requireText(resourceType, "resourceType");
        details = details == null ? Map.of() : Map.copyOf(details);
        prevHash = prevHash == null ? null : prevHash.clone();
        recordHash = recordHash == null ? null : recordHash.clone();

        // The timestamp is hashed, so its precision must match what the store can hold.
        // Instant carries nanoseconds; PostgreSQL TIMESTAMPTZ resolves to microseconds. Hashing
        // the untruncated value would produce a digest that no longer matches the record once it
        // is read back — a false tamper alarm on every event with sub-microsecond precision.
        // Truncating here, at construction, makes the hashed value and the stored value identical
        // by definition rather than by luck.
        occurredAt = occurredAt.truncatedTo(ChronoUnit.MICROS);
        recordedAt = recordedAt == null ? null : recordedAt.truncatedTo(ChronoUnit.MICROS);
    }

    private static void requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be null or blank");
        }
    }

    /** @return defensive copy of the preceding record's hash */
    @Override
    public byte[] prevHash() {
        return prevHash == null ? null : prevHash.clone();
    }

    /** @return defensive copy of this record's hash */
    @Override
    public byte[] recordHash() {
        return recordHash == null ? null : recordHash.clone();
    }

    /**
     * Returns a copy of this record linked into the chain behind {@code previousHash}.
     *
     * @param previousHash the preceding record's hash
     * @return a linked, hashed record ready to append
     */
    public AuditRecord linkedTo(byte[] previousHash) {
        byte[] computed = HashChain.hash(this, previousHash);
        return new AuditRecord(seq, auditId, occurredAt, recordedAt, actorId, actorType, actorIp,
                sessionId, action, resourceType, resourceId, outcome, details, correlationId,
                previousHash, computed);
    }
}
