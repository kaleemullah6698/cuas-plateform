package pk.nust.cuas.audit.infrastructure.persistence;

import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import pk.nust.cuas.audit.domain.AuditRepository;
import pk.nust.cuas.audit.domain.model.AuditRecord;
import pk.nust.cuas.audit.domain.model.HashChain;
import pk.nust.cuas.audit.infrastructure.persistence.entity.AuditLogEntity;
import pk.nust.cuas.audit.infrastructure.persistence.mapper.AuditRecordMapper;
import pk.nust.cuas.audit.infrastructure.persistence.repository.AuditLogJpaRepository;

/**
 * PostgreSQL-backed implementation of the audit log port.
 *
 * <p><strong>Serialised append.</strong> Reading the chain head and inserting behind it must be
 * atomic with respect to other writers: two transactions that read the same head would each link
 * to it, forking the chain and breaking verification for everything after. A transaction-scoped
 * PostgreSQL advisory lock serialises the read-compute-insert sequence. It is released
 * automatically at commit or rollback, so a crashed writer cannot wedge the log.
 *
 * <p>An advisory lock is used rather than {@code SELECT … FOR UPDATE} because the head row is a
 * moving target — each append creates a new head — so there is no stable row to lock.
 */
@Repository
public class JpaAuditRepository implements AuditRepository {

    /** Arbitrary but fixed key identifying the audit-append lock within this database. */
    private static final long APPEND_LOCK_KEY = 0x0A0D17_10L;

    private final AuditLogJpaRepository jpaRepository;
    private final AuditRecordMapper mapper;
    private final EntityManager entityManager;

    public JpaAuditRepository(AuditLogJpaRepository jpaRepository,
                              AuditRecordMapper mapper,
                              EntityManager entityManager) {
        this.jpaRepository = jpaRepository;
        this.mapper = mapper;
        this.entityManager = entityManager;
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRED)
    public AuditRecord append(AuditRecord record) {
        acquireAppendLock();

        byte[] previousHash = jpaRepository.findFirstByOrderBySeqDesc()
                .map(AuditLogEntity::getRecordHash)
                .orElse(HashChain.GENESIS_HASH);

        AuditRecord linked = record.linkedTo(previousHash);
        AuditLogEntity saved = jpaRepository.saveAndFlush(mapper.toEntity(linked));
        // recorded_at is a database default, so the row must be re-read to observe it.
        entityManager.refresh(saved);
        return mapper.toDomain(saved);
    }

    /**
     * Takes the transaction-scoped advisory lock. Blocks until the current appender commits.
     */
    private void acquireAppendLock() {
        entityManager.createNativeQuery("SELECT pg_advisory_xact_lock(:key)")
                .setParameter("key", APPEND_LOCK_KEY)
                .getSingleResult();
    }

    @Override
    @Transactional(readOnly = true)
    public boolean existsByAuditId(UUID auditId) {
        return jpaRepository.existsByAuditId(auditId);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<AuditRecord> findChainHead() {
        return jpaRepository.findFirstByOrderBySeqDesc().map(mapper::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public List<AuditRecord> findSegment(long fromSeq, long toSeq) {
        return jpaRepository.findBySeqBetweenOrderBySeqAsc(fromSeq, toSeq).stream()
                .map(mapper::toDomain)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<AuditRecord> search(String actorId, String action, String resourceType,
                                    Instant from, Instant to, int limit, int offset) {
        return jpaRepository
                .search(actorId, action, resourceType, from, to,
                        PageRequest.of(offset / Math.max(limit, 1), limit))
                .stream()
                .map(mapper::toDomain)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public long count() {
        return jpaRepository.count();
    }
}
