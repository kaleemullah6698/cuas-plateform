package pk.nust.cuas.audit.domain.service;

import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pk.nust.cuas.audit.domain.AuditRepository;
import pk.nust.cuas.audit.domain.model.AuditRecord;
import pk.nust.cuas.audit.domain.model.HashChain;

/**
 * Appends records to the audit chain.
 *
 * <p>Linking is done here rather than by the caller: the service reads the current chain head,
 * computes the record's digest over it, and appends — so a publisher never chooses its own
 * position in the chain.
 *
 * <p><strong>Idempotency.</strong> The audit topic is at-least-once (section 5.7), so the same
 * event can arrive twice. A duplicate {@code auditId} is skipped rather than appended; re-adding
 * it would insert a second copy into the chain and permanently distort the record.
 */
public class AuditAppendService {

    private static final Logger log = LoggerFactory.getLogger(AuditAppendService.class);

    private final AuditRepository repository;

    public AuditAppendService(AuditRepository repository) {
        this.repository = repository;
    }

    /**
     * Appends a record, or returns the existing one when it has already been recorded.
     *
     * @param record the record to append; its chain fields are ignored and recomputed
     * @return the appended record, or empty when it was a duplicate delivery
     */
    public Optional<AuditRecord> append(AuditRecord record) {
        if (repository.existsByAuditId(record.auditId())) {
            log.debug("Audit event {} already recorded; skipping duplicate delivery",
                    record.auditId());
            return Optional.empty();
        }
        return Optional.of(repository.append(record));
    }

    /**
     * @return the hash the next appended record will chain to
     */
    public byte[] currentHead() {
        return repository.findChainHead()
                .map(AuditRecord::recordHash)
                .orElse(HashChain.GENESIS_HASH);
    }
}
