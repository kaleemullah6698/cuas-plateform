package pk.nust.cuas.audit.domain.service;

import java.util.UUID;

/**
 * Outcome of verifying a segment of the audit chain.
 *
 * <p>A break identifies the <em>first</em> offending record: everything after it is unverifiable
 * regardless, so naming the first break is what an auditor acts on.
 *
 * @param intact           whether the segment verified
 * @param recordsVerified  how many records were walked
 * @param headHash         chain head after the segment, when intact; {@code null} when broken
 * @param brokenAtSeq      {@code seq} of the first broken record, or {@code null} when intact
 * @param brokenAtAuditId  audit id of the first broken record, or {@code null} when intact
 * @param reason           what was wrong, or {@code null} when intact
 */
public record ChainVerificationResult(
        boolean intact,
        int recordsVerified,
        byte[] headHash,
        Long brokenAtSeq,
        UUID brokenAtAuditId,
        String reason) {

    /**
     * @param recordsVerified number of records walked
     * @param headHash        the chain head after the segment
     * @return an intact result
     */
    public static ChainVerificationResult intact(int recordsVerified, byte[] headHash) {
        return new ChainVerificationResult(true, recordsVerified,
                headHash == null ? null : headHash.clone(), null, null, null);
    }

    /**
     * @param seq     position of the first broken record
     * @param auditId identity of the first broken record
     * @param reason  what was wrong
     * @return a broken result
     */
    public static ChainVerificationResult broken(Long seq, UUID auditId, String reason) {
        return new ChainVerificationResult(false, 0, null, seq, auditId, reason);
    }

    /** @return defensive copy of the chain head */
    @Override
    public byte[] headHash() {
        return headHash == null ? null : headHash.clone();
    }
}
