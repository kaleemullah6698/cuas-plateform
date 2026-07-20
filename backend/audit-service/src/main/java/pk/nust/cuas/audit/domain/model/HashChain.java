package pk.nust.cuas.audit.domain.model;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Computes and checks the audit chain digests (ARCHITECTURE.md section 7.5).
 *
 * <p>Each record's hash covers the preceding record's hash, so altering any historical row
 * invalidates every hash after it. Detecting tampering therefore needs only a re-walk of the
 * chain, not a comparison against an external copy.
 *
 * <pre>
 * record_hash = SHA256(prev_hash ‖ audit_id ‖ timestamp ‖ actor_id ‖ action ‖
 *                      resource_type ‖ resource_id ‖ outcome ‖ canonical_json(details))
 * </pre>
 *
 * <p>Fields are joined with a delimiter that cannot appear in the inputs. Plain concatenation
 * would let two different records produce identical input — {@code ("ab","c")} and
 * {@code ("a","bc")} — which is a forgery opportunity, so each field is length-delimited.
 */
public final class HashChain {

    /** Digest length in bytes. */
    public static final int HASH_LENGTH = 32;

    /** {@code prev_hash} of the first record in the chain: 32 zero bytes. */
    public static final byte[] GENESIS_HASH = new byte[HASH_LENGTH];

    private static final String ALGORITHM = "SHA-256";

    private HashChain() {
    }

    /**
     * Computes the digest of a record positioned behind {@code prevHash}.
     *
     * @param record   the record to hash; its own {@code prevHash}/{@code recordHash} are ignored
     * @param prevHash the preceding record's hash
     * @return the record digest
     */
    public static byte[] hash(AuditRecord record, byte[] prevHash) {
        MessageDigest digest = newDigest();
        digest.update(prevHash);
        update(digest, record.auditId().toString());
        // Instant.toString() is ISO-8601 and stable; the epoch pair avoids any formatting drift.
        update(digest, record.occurredAt().getEpochSecond() + "." + record.occurredAt().getNano());
        update(digest, record.actorId());
        update(digest, record.action());
        update(digest, record.resourceType());
        update(digest, record.resourceId());
        update(digest, record.outcome().name());
        update(digest, CanonicalJson.of(record.details()));
        return digest.digest();
    }

    /**
     * Verifies that a record's stored hash matches its content and its declared predecessor.
     *
     * @param record   the record to check
     * @param prevHash the hash the record should be chained to
     * @return {@code true} when the record is intact
     */
    public static boolean isIntact(AuditRecord record, byte[] prevHash) {
        return MessageDigest.isEqual(record.recordHash(), hash(record, prevHash));
    }

    /**
     * Length-delimits a field before mixing it in, so field boundaries are unambiguous.
     *
     * @param digest the digest to update
     * @param value  the field value; {@code null} is distinguished from empty
     */
    private static void update(MessageDigest digest, String value) {
        if (value == null) {
            // A distinct marker: absent must not hash the same as present-and-empty.
            digest.update((byte) 0x00);
            return;
        }
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        digest.update((byte) 0x01);
        digest.update(intToBytes(bytes.length));
        digest.update(bytes);
    }

    private static byte[] intToBytes(int value) {
        return new byte[] {
                (byte) (value >>> 24), (byte) (value >>> 16), (byte) (value >>> 8), (byte) value
        };
    }

    private static MessageDigest newDigest() {
        try {
            return MessageDigest.getInstance(ALGORITHM);
        } catch (NoSuchAlgorithmException ex) {
            // SHA-256 is mandated by the JLS for every conforming JRE.
            throw new IllegalStateException(ALGORITHM + " is unavailable", ex);
        }
    }
}
