package pk.nust.cuas.audit.domain.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import pk.nust.cuas.audit.domain.model.AuditRecord;
import pk.nust.cuas.audit.domain.model.HashChain;
import pk.nust.cuas.commons.audit.ActorType;
import pk.nust.cuas.commons.audit.AuditOutcome;

/**
 * Verification must detect each way a chain can be broken: an altered row, a removed row, and a
 * reordered row. Each is asserted separately because they fail through different checks.
 */
class ChainVerifierTest {

    private final ChainVerifier verifier = new ChainVerifier();

    private static AuditRecord unlinked(long seq, String action) {
        return new AuditRecord(seq, UUID.randomUUID(), Instant.parse("2026-07-20T10:00:00Z"), null,
                "usr_1", ActorType.USER, null, null, action, "USER", "r" + seq,
                AuditOutcome.SUCCESS, Map.of("n", String.valueOf(seq)), null, null, null);
    }

    /** Builds a correctly linked chain of the given length. */
    private static List<AuditRecord> chainOf(int length) {
        List<AuditRecord> chain = new ArrayList<>();
        byte[] previous = HashChain.GENESIS_HASH;
        for (int i = 1; i <= length; i++) {
            AuditRecord linked = unlinked(i, "ACTION_" + i).linkedTo(previous);
            chain.add(linked);
            previous = linked.recordHash();
        }
        return chain;
    }

    @Test
    void emptySegmentIsIntact() {
        ChainVerificationResult result = verifier.verify(List.of(), HashChain.GENESIS_HASH);

        assertThat(result.intact()).isTrue();
        assertThat(result.recordsVerified()).isZero();
    }

    @Test
    void wellFormedChainVerifies() {
        List<AuditRecord> chain = chainOf(5);

        ChainVerificationResult result = verifier.verify(chain, HashChain.GENESIS_HASH);

        assertThat(result.intact()).isTrue();
        assertThat(result.recordsVerified()).isEqualTo(5);
        assertThat(result.headHash()).isEqualTo(chain.get(4).recordHash());
    }

    @Test
    void alteredRecordIsDetectedAndLocated() {
        List<AuditRecord> chain = new ArrayList<>(chainOf(5));
        AuditRecord original = chain.get(2);
        // Content changed, stored hashes left intact — the signature of an in-place edit.
        chain.set(2, new AuditRecord(original.seq(), original.auditId(), original.occurredAt(),
                original.recordedAt(), original.actorId(), original.actorType(), original.actorIp(),
                original.sessionId(), "TAMPERED", original.resourceType(), original.resourceId(),
                original.outcome(), original.details(), original.correlationId(),
                original.prevHash(), original.recordHash()));

        ChainVerificationResult result = verifier.verify(chain, HashChain.GENESIS_HASH);

        assertThat(result.intact()).isFalse();
        assertThat(result.brokenAtSeq()).isEqualTo(3L);
        assertThat(result.reason()).contains("record_hash");
    }

    @Test
    void removedRecordIsDetected() {
        List<AuditRecord> chain = new ArrayList<>(chainOf(5));
        chain.remove(2);   // the successor's prev_hash now points at a record that is not there

        ChainVerificationResult result = verifier.verify(chain, HashChain.GENESIS_HASH);

        assertThat(result.intact()).isFalse();
        assertThat(result.reason()).contains("prev_hash");
    }

    @Test
    void reorderedRecordsAreDetected() {
        List<AuditRecord> chain = new ArrayList<>(chainOf(5));
        AuditRecord third = chain.get(2);
        chain.set(2, chain.get(3));
        chain.set(3, third);

        ChainVerificationResult result = verifier.verify(chain, HashChain.GENESIS_HASH);

        assertThat(result.intact()).isFalse();
    }

    @Test
    void wrongStartingHashIsDetected() {
        List<AuditRecord> chain = chainOf(3);
        byte[] wrongStart = new byte[32];
        wrongStart[0] = 9;

        ChainVerificationResult result = verifier.verify(chain, wrongStart);

        assertThat(result.intact()).isFalse();
        assertThat(result.brokenAtSeq()).isEqualTo(1L);
    }

    @Test
    void firstBreakIsReportedNotTheLast() {
        List<AuditRecord> chain = new ArrayList<>(chainOf(5));
        chain.remove(3);
        chain.remove(1);

        ChainVerificationResult result = verifier.verify(chain, HashChain.GENESIS_HASH);

        assertThat(result.intact()).isFalse();
        // seq 3 is the first record whose predecessor is missing.
        assertThat(result.brokenAtSeq()).isEqualTo(3L);
    }
}
