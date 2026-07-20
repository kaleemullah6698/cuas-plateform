package pk.nust.cuas.gateway.domain.service;

import pk.nust.cuas.gateway.domain.model.RateLimitDecision;
import pk.nust.cuas.gateway.domain.model.RateLimitKey;

/**
 * Port for consuming a token from a bucket (ARCHITECTURE.md section 7.8).
 *
 * <p>Declared here, in the domain, and implemented outward in {@code infrastructure/cache} —
 * the gateway's policies decide <em>which</em> bucket a request belongs to without knowing that
 * the buckets happen to live in Redis.
 *
 * <p>Implementations must be atomic across gateway instances. The gateway scales horizontally
 * (section 4.1), so a read-modify-write that is only atomic per process is not a rate limiter:
 * three instances would each grant the full quota.
 */
public interface RateLimiter {

    /**
     * Attempts to take one token from the bucket identified by {@code key}.
     *
     * @param key the bucket, carrying the rule that governs its size and refill rate
     * @return the decision, always populated with the header values of section 7.8
     * @throws EdgeStateUnavailableException when the backing store cannot be reached
     */
    RateLimitDecision tryConsume(RateLimitKey key);
}
