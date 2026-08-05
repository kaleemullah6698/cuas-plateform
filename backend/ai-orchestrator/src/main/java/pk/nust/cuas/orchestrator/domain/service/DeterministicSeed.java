package pk.nust.cuas.orchestrator.domain.service;

import java.nio.charset.StandardCharsets;

/**
 * Turns identifiers into stable seeds (ARCHITECTURE.md section 6.4, "seeded by {@code trackId}").
 *
 * <p>Every random-looking decision the mock makes — which scenario, how long it took, whether it
 * fails — is derived from a seed built here, so it is reproducible run to run, machine to machine,
 * and across restarts. A demonstration replays identically; a failing test can be re-run.
 *
 * <p>Uses a 64-bit FNV-1a hash rather than {@link String#hashCode()}. {@code String.hashCode} is a
 * 32-bit value with poor avalanche on short, similar inputs — and track ids are UUIDv7, which share a
 * long common prefix by construction. Seeding from it would cluster consecutive tracks into the same
 * scenario, which is precisely the opposite of what a scenario library is for.
 */
final class DeterministicSeed {

    private static final long FNV_OFFSET_BASIS = 0xcbf29ce484222325L;
    private static final long FNV_PRIME = 0x100000001b3L;

    private DeterministicSeed() {
    }

    /**
     * @param parts the identifiers to combine, e.g. a track id and a service id
     * @return a stable 64-bit seed
     */
    static long of(String... parts) {
        long hash = FNV_OFFSET_BASIS;
        for (String part : parts) {
            for (byte b : part.getBytes(StandardCharsets.UTF_8)) {
                hash ^= (b & 0xff);
                hash *= FNV_PRIME;
            }
            // A separator, so ("ab","c") and ("a","bc") do not collide.
            hash ^= 0xff;
            hash *= FNV_PRIME;
        }
        return hash;
    }

    /**
     * @param seed a seed
     * @return a value in [0,1), used to compare against configured rates
     */
    static double toUnitInterval(long seed) {
        // The top 53 bits are the well-mixed ones after FNV, and 53 is exactly a double's mantissa.
        return (seed >>> 11) * 0x1.0p-53;
    }
}
