package pk.nust.cuas.audit.infrastructure.persistence.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import pk.nust.cuas.audit.infrastructure.persistence.entity.AuditLogEntity;

/**
 * Spring Data access to {@code audit_log}.
 *
 * <p>Read and insert only: the append-only guarantee is enforced by database rules
 * (ARCHITECTURE.md section 7.5), and no update or delete method is exposed here so that
 * application code cannot even express the attempt.
 */
public interface AuditLogJpaRepository extends JpaRepository<AuditLogEntity, Long> {

    boolean existsByAuditId(UUID auditId);

    Optional<AuditLogEntity> findFirstByOrderBySeqDesc();

    List<AuditLogEntity> findBySeqBetweenOrderBySeqAsc(long fromSeq, long toSeq);

    /**
     * Auditor query with independently optional filters.
     *
     * <p>Each predicate is disabled by passing {@code null}, which keeps one query plan rather
     * than assembling SQL by string concatenation.
     */
    @Query("""
            SELECT a FROM AuditLogEntity a
            WHERE (:actorId      IS NULL OR a.actorId      = :actorId)
              AND (:action       IS NULL OR a.action       = :action)
              AND (:resourceType IS NULL OR a.resourceType = :resourceType)
              AND (CAST(:from AS timestamp) IS NULL OR a.occurredAt >= :from)
              AND (CAST(:to   AS timestamp) IS NULL OR a.occurredAt <= :to)
            ORDER BY a.seq DESC
            """)
    List<AuditLogEntity> search(@Param("actorId") String actorId,
                                @Param("action") String action,
                                @Param("resourceType") String resourceType,
                                @Param("from") Instant from,
                                @Param("to") Instant to,
                                org.springframework.data.domain.Pageable pageable);
}
