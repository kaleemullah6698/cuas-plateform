package pk.nust.cuas.audit.domain.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import pk.nust.cuas.commons.audit.ActorType;
import pk.nust.cuas.commons.audit.AuditOutcome;

/**
 * The hash chain is the tamper-evidence mechanism, so these tests assert the properties an
 * attacker would need to violate: field changes must alter the digest, field boundaries must be
 * unambiguous, and absent must differ from empty.
 */
class HashChainTest {

    private static AuditRecord record(String action, String resourceId, Map<String, String> details) {
        return new AuditRecord(null, UUID.fromString("0189d4f1-0000-7000-8000-000000000001"),
                Instant.parse("2026-07-20T10:15:30Z"), null,
                "usr_1", ActorType.USER, null, null,
                action, "USER", resourceId, AuditOutcome.SUCCESS, details, null, null, null);
    }

    @Test
    void genesisHashIsThirtyTwoZeroBytes() {
        assertThat(HashChain.GENESIS_HASH).hasSize(32).containsOnly((byte) 0);
    }

    @Test
    void hashIsDeterministicForIdenticalContent() {
        AuditRecord a = record("LOGIN_SUCCEEDED", "usr_1", Map.of("ip", "10.0.0.1"));
        AuditRecord b = record("LOGIN_SUCCEEDED", "usr_1", Map.of("ip", "10.0.0.1"));

        assertThat(HashChain.hash(a, HashChain.GENESIS_HASH))
                .isEqualTo(HashChain.hash(b, HashChain.GENESIS_HASH));
    }

    @Test
    void changingAnyFieldChangesTheHash() {
        byte[] baseline = HashChain.hash(record("A", "r1", Map.of()), HashChain.GENESIS_HASH);

        assertThat(HashChain.hash(record("B", "r1", Map.of()), HashChain.GENESIS_HASH))
                .isNotEqualTo(baseline);
        assertThat(HashChain.hash(record("A", "r2", Map.of()), HashChain.GENESIS_HASH))
                .isNotEqualTo(baseline);
        assertThat(HashChain.hash(record("A", "r1", Map.of("k", "v")), HashChain.GENESIS_HASH))
                .isNotEqualTo(baseline);
    }

    @Test
    void changingThePredecessorChangesTheHash() {
        AuditRecord r = record("A", "r1", Map.of());
        byte[] otherPrev = new byte[32];
        otherPrev[0] = 1;

        assertThat(HashChain.hash(r, otherPrev))
                .isNotEqualTo(HashChain.hash(r, HashChain.GENESIS_HASH));
    }

    @Test
    void fieldBoundariesAreUnambiguous() {
        // Plain concatenation would hash ("ab","c") and ("a","bc") identically, letting an
        // attacker move characters across a field boundary without changing the digest.
        byte[] first = HashChain.hash(record("ab", "c", Map.of()), HashChain.GENESIS_HASH);
        byte[] second = HashChain.hash(record("a", "bc", Map.of()), HashChain.GENESIS_HASH);

        assertThat(first).isNotEqualTo(second);
    }

    @Test
    void absentFieldDiffersFromEmptyField() {
        byte[] absent = HashChain.hash(record("A", null, Map.of()), HashChain.GENESIS_HASH);
        byte[] empty = HashChain.hash(record("A", "", Map.of()), HashChain.GENESIS_HASH);

        assertThat(absent).isNotEqualTo(empty);
    }

    @Test
    void isIntactAcceptsAnUnmodifiedRecord() {
        AuditRecord linked = record("A", "r1", Map.of("k", "v")).linkedTo(HashChain.GENESIS_HASH);

        assertThat(HashChain.isIntact(linked, HashChain.GENESIS_HASH)).isTrue();
    }

    @Test
    void isIntactRejectsAModifiedRecord() {
        AuditRecord linked = record("A", "r1", Map.of()).linkedTo(HashChain.GENESIS_HASH);
        // Same stored hash, but the action has been altered — exactly what tampering looks like.
        AuditRecord tampered = new AuditRecord(linked.seq(), linked.auditId(), linked.occurredAt(),
                linked.recordedAt(), linked.actorId(), linked.actorType(), linked.actorIp(),
                linked.sessionId(), "TAMPERED", linked.resourceType(), linked.resourceId(),
                linked.outcome(), linked.details(), linked.correlationId(),
                linked.prevHash(), linked.recordHash());

        assertThat(HashChain.isIntact(tampered, HashChain.GENESIS_HASH)).isFalse();
    }

    @Test
    void hashIsThirtyTwoBytes() {
        assertThat(HashChain.hash(record("A", "r", Map.of()), HashChain.GENESIS_HASH)).hasSize(32);
    }
}
