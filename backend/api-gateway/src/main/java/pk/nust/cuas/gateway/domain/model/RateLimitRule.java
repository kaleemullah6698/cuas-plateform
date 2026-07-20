package pk.nust.cuas.gateway.domain.model;

import java.time.Duration;
import java.util.Objects;

/**
 * One row of the ARCHITECTURE.md section 7.8 rate-limit table, expressed as a token bucket.
 *
 * <p>The two numbers in that table map onto a bucket exactly: <em>burst</em> is the bucket's
 * capacity — the most requests that may be served back to back from a standing start — and
 * <em>limit per window</em> is the refill rate. Reading them the other way round (capacity =
 * limit + burst) would let {@code POST /auth/login} accept ten immediate attempts where the table
 * says five, so the stricter reading is the correct one.
 *
 * @param name           stable identifier for the rule, used in metrics, logs and audit records
 * @param limit          sustained number of requests permitted per {@code window}
 * @param window         the period over which {@code limit} tokens are replenished
 * @param burst          bucket capacity: the largest instantaneous run of requests permitted
 * @param securitySensitive whether a rejection under this rule must be audited (section 7.8)
 */
public record RateLimitRule(
        String name,
        long limit,
        Duration window,
        long burst,
        boolean securitySensitive) {

    public RateLimitRule {
        Objects.requireNonNull(window, "window");
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("name must not be null or blank");
        }
        if (limit <= 0) {
            throw new IllegalArgumentException("limit must be positive, was " + limit);
        }
        if (burst <= 0) {
            throw new IllegalArgumentException("burst must be positive, was " + burst);
        }
        if (window.isZero() || window.isNegative()) {
            throw new IllegalArgumentException("window must be positive, was " + window);
        }
    }

    /**
     * Rate at which tokens are replenished, in tokens per second.
     *
     * <p>Fractional by design: {@code 5 / hour} is a little under one token every twelve minutes,
     * and rounding that to an integer would either forbid the fifth request or permit a sixth.
     *
     * @return tokens replenished per second
     */
    public double refillTokensPerSecond() {
        return (double) limit / window.toMillis() * 1000.0d;
    }
}
