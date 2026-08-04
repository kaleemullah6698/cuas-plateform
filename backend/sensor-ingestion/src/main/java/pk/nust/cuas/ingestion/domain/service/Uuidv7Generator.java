package pk.nust.cuas.ingestion.domain.service;

import java.time.Instant;
import java.util.UUID;
import java.util.random.RandomGenerator;

/**
 * Generates UUIDv7 identifiers for accepted detections (ARCHITECTURE.md section 5.3).
 *
 * <p>UUIDv7 embeds a 48-bit Unix-millisecond timestamp in its most-significant bits, so the ids are
 * time-sortable: they index well in PostgreSQL B-trees and sort naturally in Elasticsearch, which
 * is the reason the canonical schema mandates them over random UUIDv4. The remaining 74 bits are
 * random, which keeps collisions negligible even at high per-millisecond volume.
 *
 * <p>The JDK has no built-in UUIDv7, so the layout (RFC 9562 §5.7) is assembled here from JDK
 * primitives only, keeping the domain free of third-party dependencies. The random source is
 * injected so a test can make generation deterministic.
 */
public final class Uuidv7Generator {

    private static final long VERSION_7 = 0x7000L;              // version nibble in bits 12-15 of the low word
    private static final long VARIANT_RFC4122 = 0x8000000000000000L; // variant '10' in the two MSBs of the LSB

    private final RandomGenerator random;

    /**
     * @param random the source of the 74 random bits; inject a seeded generator for deterministic tests
     */
    public Uuidv7Generator(RandomGenerator random) {
        this.random = random;
    }

    /**
     * @param timestamp the instant to embed; normally the platform ingest time
     * @return a UUIDv7 whose timestamp field is {@code timestamp} truncated to milliseconds
     */
    public UUID generate(Instant timestamp) {
        long unixMillis = timestamp.toEpochMilli();

        // Most-significant 64 bits: 48-bit timestamp, 4-bit version, 12 random bits (rand_a).
        long randA = random.nextLong() & 0x0FFFL;
        long msb = (unixMillis & 0xFFFFFFFFFFFFL) << 16 | VERSION_7 | randA;

        // Least-significant 64 bits: 2-bit variant then 62 random bits (rand_b).
        long randB = random.nextLong() & 0x3FFFFFFFFFFFFFFFL;
        long lsb = VARIANT_RFC4122 | randB;

        return new UUID(msb, lsb);
    }
}
