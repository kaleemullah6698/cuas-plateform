package pk.nust.cuas.gateway.domain.model;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/**
 * The outcome of consuming a token, carrying everything the response headers of section 7.8 need.
 *
 * <p>Both outcomes carry the full header set, not just rejections: a client that can see its
 * remaining quota on every response can pace itself instead of discovering the limit by being
 * refused.
 *
 * @param allowed   whether the request may proceed
 * @param limit     the sustained limit per window, for {@code X-RateLimit-Limit}
 * @param remaining whole tokens left in the bucket, for {@code X-RateLimit-Remaining}
 * @param resetAt   when the next token becomes available, for {@code X-RateLimit-Reset}
 */
public record RateLimitDecision(boolean allowed, long limit, long remaining, Instant resetAt) {

    public RateLimitDecision {
        Objects.requireNonNull(resetAt, "resetAt");
        if (remaining < 0) {
            throw new IllegalArgumentException("remaining must not be negative, was " + remaining);
        }
    }

    /**
     * Seconds a rejected caller should wait, for {@code Retry-After}.
     *
     * <p>Never less than one: {@code Retry-After: 0} invites an immediate retry, which is the
     * behaviour the limit exists to prevent.
     *
     * @param now the current instant
     * @return whole seconds until the next token, at least one
     */
    public long retryAfterSeconds(Instant now) {
        long seconds = Duration.between(now, resetAt).toSeconds();
        return Math.max(1L, seconds);
    }
}
