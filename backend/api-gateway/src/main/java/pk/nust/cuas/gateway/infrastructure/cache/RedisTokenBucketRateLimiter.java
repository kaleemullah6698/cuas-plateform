package pk.nust.cuas.gateway.infrastructure.cache;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;
import pk.nust.cuas.gateway.domain.exception.EdgeStateUnavailableException;
import pk.nust.cuas.gateway.domain.model.RateLimitDecision;
import pk.nust.cuas.gateway.domain.model.RateLimitKey;
import pk.nust.cuas.gateway.domain.model.RateLimitRule;
import pk.nust.cuas.gateway.domain.service.RateLimiter;

/**
 * Redis-backed token buckets (ARCHITECTURE.md sections 4.1.1, 7.8).
 *
 * <p>The refill-and-consume step runs as a Lua script so that it is atomic across every gateway
 * instance; see {@code redis/token-bucket.lua} for why that matters. The current time is passed in
 * rather than read inside the script, so all instances measure against one clock and a replica
 * with a skewed clock cannot widen or narrow the window.
 */
@Component
public class RedisTokenBucketRateLimiter implements RateLimiter {

    /**
     * Margin added to a bucket's time-to-live. The key may only be forgotten once refill would
     * have restored it to full, since resurrecting it early would hand back a full bucket.
     */
    private static final long TTL_MARGIN_MILLIS = 60_000L;

    private final StringRedisTemplate redis;
    private final RedisScript<List> script;
    private final Clock clock;

    public RedisTokenBucketRateLimiter(StringRedisTemplate redis, Clock clock) {
        this.redis = redis;
        this.clock = clock;
        DefaultRedisScript<List> tokenBucket = new DefaultRedisScript<>();
        tokenBucket.setLocation(new ClassPathResource("redis/token-bucket.lua"));
        tokenBucket.setResultType(List.class);
        this.script = tokenBucket;
    }

    @Override
    @SuppressWarnings("unchecked")
    public RateLimitDecision tryConsume(RateLimitKey key) {
        RateLimitRule rule = key.rule();
        Instant now = clock.instant();
        double refillPerSecond = rule.refillTokensPerSecond();

        List<Long> result;
        try {
            result = redis.execute(
                    script,
                    List.of(key.storageKey()),
                    Long.toString(rule.burst()),
                    Double.toString(refillPerSecond),
                    Long.toString(now.toEpochMilli()),
                    Long.toString(ttlMillis(rule)));
        } catch (RuntimeException ex) {
            // Fail closed: see EdgeStateUnavailableException for why this is a 503 and not an
            // allowed request.
            throw new EdgeStateUnavailableException("rate-limit:" + rule.name(), ex);
        }

        if (result == null || result.size() < 3) {
            throw new EdgeStateUnavailableException("rate-limit:" + rule.name(),
                    new IllegalStateException("token bucket script returned " + result));
        }

        boolean allowed = result.get(0) != 0L;
        long remaining = result.get(1);
        Instant resetAt = now.plusMillis(result.get(2));
        return new RateLimitDecision(allowed, rule.limit(), remaining, resetAt);
    }

    private long ttlMillis(RateLimitRule rule) {
        long fullRefill = (long) Math.ceil(rule.burst() / rule.refillTokensPerSecond() * 1000.0d);
        return fullRefill + TTL_MARGIN_MILLIS;
    }
}
