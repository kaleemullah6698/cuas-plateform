package pk.nust.cuas.audit.domain.service;

import java.util.List;
import pk.nust.cuas.audit.domain.model.AuditRecord;
import pk.nust.cuas.audit.domain.model.HashChain;

/**
 * Walks a segment of the audit chain and reports the first break, if any.
 *
 * <p>Verification is sequential by nature: each record's integrity depends on its predecessor, so
 * the first broken link invalidates everything after it and is the only interesting result. The
 * verifier therefore stops at the first failure and names it, rather than reporting a count.
 *
 * <p>Two distinct failures are detected: a record whose content no longer matches its own stored
 * digest (the row was altered), and a record whose {@code prevHash} does not match the actual
 * predecessor's digest (a row was removed, reordered, or inserted).
 */
public final class ChainVerifier {

    /**
     * Verifies a contiguous, ascending-{@code seq} segment.
     *
     * @param records          the segment, ordered by {@code seq} ascending
     * @param expectedPrevHash the hash the first record must chain to — {@link HashChain#GENESIS_HASH}
     *                         when the segment starts at the beginning of the log, otherwise the
     *                         {@code recordHash} of the row before the segment
     * @return the verification outcome
     */
    public ChainVerificationResult verify(List<AuditRecord> records, byte[] expectedPrevHash) {
        if (records.isEmpty()) {
            return ChainVerificationResult.intact(0, expectedPrevHash);
        }

        byte[] previous = expectedPrevHash;
        for (AuditRecord record : records) {
            if (!java.security.MessageDigest.isEqual(record.prevHash(), previous)) {
                return ChainVerificationResult.broken(record.seq(), record.auditId(),
                        "prev_hash does not match the preceding record — a record was altered, "
                                + "removed, reordered, or inserted");
            }
            if (!HashChain.isIntact(record, previous)) {
                return ChainVerificationResult.broken(record.seq(), record.auditId(),
                        "record_hash does not match the record content — the row was altered");
            }
            previous = record.recordHash();
        }
        return ChainVerificationResult.intact(records.size(), previous);
    }
}
