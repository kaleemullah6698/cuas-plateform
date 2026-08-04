package pk.nust.cuas.commons.id;

import java.time.Instant;
import java.util.UUID;
import java.util.random.RandomGenerator;

/**
 * Generates UUIDv7 identifiers (RFC 9562 §5.7): a 48-bit Unix-millisecond timestamp in the high
 * bits followed by 74 random bits.
 *
 * <p>The timestamp prefix makes the ids time-sortable, so they index well in PostgreSQL B-trees and
 * sort naturally in Elasticsearch — the reason the platform's canonical schemas mandate UUIDv7 over
 * random UUIDv4 for detection and track identity (ARCHITECTURE.md sections 5.3, 5.3.2). Shared here
 * rather than duplicated per service (CLAUDE.md section 3). The JDK has no built-in UUIDv7, so the
 * layout is assembled from JDK primitives only. The random source is injected so a test can make
 * generation deterministic.
 */
public final class Uuidv7Generator {

    private static final long VERSION_7 = 0x7000L;                    // version nibble, low 16 bits of the MSB
    private static final long VARIANT_RFC4122 = 0x8000000000000000L;  // variant '10', two MSBs of the LSB

    private final RandomGenerator random;

    /**
     * @param random the source of the 74 random bits; inject a seeded generator for deterministic tests
     */
    public Uuidv7Generator(RandomGenerator random) {
        this.random = random;
    }

    /**
     * @param timestamp the instant to embed; normally an authoritative platform time
     * @return a UUIDv7 whose timestamp field is {@code timestamp} truncated to milliseconds
     */
    public UUID generate(Instant timestamp) {
        long unixMillis = timestamp.toEpochMilli();

        long randA = random.nextLong() & 0x0FFFL;
        long msb = (unixMillis & 0xFFFFFFFFFFFFL) << 16 | VERSION_7 | randA;

        long randB = random.nextLong() & 0x3FFFFFFFFFFFFFFFL;
        long lsb = VARIANT_RFC4122 | randB;

        return new UUID(msb, lsb);
    }
}
