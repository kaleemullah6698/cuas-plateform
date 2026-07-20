package pk.nust.cuas.audit.domain;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import pk.nust.cuas.audit.domain.model.AuditRecord;

/**
 * Persistence port for the audit log, declared by the domain and implemented by infrastructure.
 *
 * <p>There is deliberately no update or delete operation. The log is append-only (ARCHITECTURE.md
 * section 7.5) and the port reflects that: code cannot express a mutation that the database would
 * reject, so the constraint is visible at compile time rather than discovered at runtime.
 */
public interface AuditRepository {

    /**
     * Appends a record, assigning it the next position in the chain.
     *
     * <p>Implementations must serialise concurrent appends. Two writers that read the same chain
     * head would produce two records claiming the same predecessor, which breaks verification for
     * everything after them.
     *
     * @param record the record to append; its chain fields are computed during the append
     * @return the appended record, with {@code seq} and {@code recordedAt} populated
     */
    AuditRecord append(AuditRecord record);

    /**
     * @param auditId the idempotency key
     * @return {@code true} when a record with this audit id is already present
     */
    boolean existsByAuditId(UUID auditId);

    /**
     * @return the most recently appended record, or empty when the log is empty
     */
    Optional<AuditRecord> findChainHead();

    /**
     * Returns a contiguous segment ordered by {@code seq} ascending, for chain verification.
     *
     * @param fromSeq lowest {@code seq} to include, inclusive
     * @param toSeq   highest {@code seq} to include, inclusive
     * @return the segment
     */
    List<AuditRecord> findSegment(long fromSeq, long toSeq);

    /**
     * Queries the log for an auditor.
     *
     * @param actorId      restrict to this actor, or {@code null} for any
     * @param action       restrict to this action, or {@code null} for any
     * @param resourceType restrict to this resource type, or {@code null} for any
     * @param from         earliest {@code occurredAt}, or {@code null} for unbounded
     * @param to           latest {@code occurredAt}, or {@code null} for unbounded
     * @param limit        maximum rows to return
     * @param offset       rows to skip
     * @return matching records, most recent first
     */
    List<AuditRecord> search(String actorId, String action, String resourceType,
                             Instant from, Instant to, int limit, int offset);

    /**
     * @return total number of records in the log
     */
    long count();
}
