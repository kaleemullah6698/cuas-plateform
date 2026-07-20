package pk.nust.cuas.gateway.infrastructure.cache;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import pk.nust.cuas.gateway.domain.exception.EdgeStateUnavailableException;
import pk.nust.cuas.gateway.domain.model.RateLimitDecision;
import pk.nust.cuas.gateway.domain.model.RateLimitKey;
import pk.nust.cuas.gateway.domain.model.RateLimitRule;

/**
 * The section 7.8 token bucket against a real Redis.
 *
 * <p>An in-memory fake would test the arithmetic and nothing else. The properties that matter here
 * are properties of the deployment: that refill-and-consume is atomic under concurrency across
 * processes, that the Lua script runs as written on the Redis version the platform ships, and that
 * an unreachable Redis fails closed rather than open. None of those survive being mocked.
 */
@Testcontainers
class RedisTokenBucketRateLimiterIntegrationTest {

    @Container
    @SuppressWarnings("resource")
    static GenericContainer<?> redis =
            new GenericContainer<>("redis:7.4-alpine").withExposedPorts(6379);

    private static LettuceConnectionFactory connectionFactory;
    private static StringRedisTemplate template;

    /** A clock the test moves by hand, so refill can be observed without sleeping. */
    private static final class MovableClock extends Clock {
        private Instant now = Instant.parse("2026-07-20T10:00:00Z");

        @Override public java.time.ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override public Instant instant() {
            return now;
        }

        void advance(Duration amount) {
            now = now.plus(amount);
        }
    }

    @BeforeAll
    static void startTemplate() {
        connectionFactory = new LettuceConnectionFactory(
                redis.getHost(), redis.getMappedPort(6379));
        connectionFactory.afterPropertiesSet();
        template = new StringRedisTemplate(connectionFactory);
        template.afterPropertiesSet();
    }

    @AfterAll
    static void stopTemplate() {
        connectionFactory.destroy();
    }

    private static RateLimitKey freshKey(RateLimitRule rule) {
        return new RateLimitKey(rule, UUID.randomUUID().toString());
    }

    private static final RateLimitRule LOGIN_PER_IP =
            new RateLimitRule("login-per-ip", 5, Duration.ofMinutes(1), 5, true);

    @Test
    void aBucketPermitsItsBurstAndThenRefuses() {
        var limiter = new RedisTokenBucketRateLimiter(template, new MovableClock());
        RateLimitKey key = freshKey(LOGIN_PER_IP);

        for (int attempt = 1; attempt <= 5; attempt++) {
            assertThat(limiter.tryConsume(key).allowed())
                    .as("attempt %d of the 5-token burst", attempt).isTrue();
        }
        assertThat(limiter.tryConsume(key).allowed()).isFalse();
    }

    @Test
    void remainingCountsDownAndTheLimitIsTheTableValue() {
        var limiter = new RedisTokenBucketRateLimiter(template, new MovableClock());
        RateLimitKey key = freshKey(LOGIN_PER_IP);

        assertThat(limiter.tryConsume(key).remaining()).isEqualTo(4);
        assertThat(limiter.tryConsume(key).remaining()).isEqualTo(3);
        assertThat(limiter.tryConsume(key).limit()).isEqualTo(5);
    }

    @Test
    void anExhaustedBucketRefillsAtTheTableRate() {
        MovableClock clock = new MovableClock();
        var limiter = new RedisTokenBucketRateLimiter(template, clock);
        RateLimitKey key = freshKey(LOGIN_PER_IP);

        for (int i = 0; i < 5; i++) {
            limiter.tryConsume(key);
        }
        assertThat(limiter.tryConsume(key).allowed()).isFalse();

        // 5 per minute is one token every twelve seconds.
        clock.advance(Duration.ofSeconds(12));
        assertThat(limiter.tryConsume(key).allowed()).isTrue();
        assertThat(limiter.tryConsume(key).allowed()).isFalse();
    }

    @Test
    void aBucketNeverRefillsBeyondItsBurst() {
        MovableClock clock = new MovableClock();
        var limiter = new RedisTokenBucketRateLimiter(template, clock);
        RateLimitKey key = freshKey(LOGIN_PER_IP);

        limiter.tryConsume(key);
        // An hour of idleness must not bank an hour's worth of attempts.
        clock.advance(Duration.ofHours(1));

        for (int i = 0; i < 5; i++) {
            assertThat(limiter.tryConsume(key).allowed()).isTrue();
        }
        assertThat(limiter.tryConsume(key).allowed()).isFalse();
    }

    @Test
    void oneIdentityExhaustingItsQuotaDoesNotAffectAnother() {
        var limiter = new RedisTokenBucketRateLimiter(template, new MovableClock());
        RateLimitKey attacker = freshKey(LOGIN_PER_IP);
        RateLimitKey bystander = freshKey(LOGIN_PER_IP);

        for (int i = 0; i < 6; i++) {
            limiter.tryConsume(attacker);
        }

        assertThat(limiter.tryConsume(bystander).allowed()).isTrue();
    }

    @Test
    void aClockThatStepsBackwardsDoesNotRefillTheBucket() {
        MovableClock clock = new MovableClock();
        var limiter = new RedisTokenBucketRateLimiter(template, clock);
        RateLimitKey key = freshKey(LOGIN_PER_IP);

        for (int i = 0; i < 5; i++) {
            limiter.tryConsume(key);
        }
        clock.advance(Duration.ofMinutes(-30));

        assertThat(limiter.tryConsume(key).allowed()).isFalse();
    }

    @Test
    void concurrentCallersShareOneQuotaRatherThanOneEach() throws Exception {
        var limiter = new RedisTokenBucketRateLimiter(template, new MovableClock());
        RateLimitKey key = freshKey(LOGIN_PER_IP);

        int callers = 40;
        var allowed = new AtomicInteger();
        var start = new CountDownLatch(1);
        var finished = new CountDownLatch(callers);
        ExecutorService pool = Executors.newFixedThreadPool(16);
        try {
            for (int i = 0; i < callers; i++) {
                pool.submit(() -> {
                    try {
                        start.await();
                        if (limiter.tryConsume(key).allowed()) {
                            allowed.incrementAndGet();
                        }
                    } catch (InterruptedException ex) {
                        Thread.currentThread().interrupt();
                    } finally {
                        finished.countDown();
                    }
                });
            }
            start.countDown();
            assertThat(finished.await(30, TimeUnit.SECONDS)).isTrue();
        } finally {
            pool.shutdownNow();
        }

        // Exactly the burst, not one per thread. This is the property the Lua script exists for:
        // read-modify-write in Java would let each caller see the same starting balance.
        assertThat(allowed.get()).isEqualTo(5);
    }

    @Test
    void aRejectionCarriesAUsableRetryHint() {
        MovableClock clock = new MovableClock();
        var limiter = new RedisTokenBucketRateLimiter(template, clock);
        RateLimitKey key = freshKey(LOGIN_PER_IP);

        for (int i = 0; i < 5; i++) {
            limiter.tryConsume(key);
        }
        RateLimitDecision refused = limiter.tryConsume(key);

        assertThat(refused.allowed()).isFalse();
        assertThat(refused.remaining()).isZero();
        assertThat(refused.retryAfterSeconds(clock.instant())).isBetween(1L, 12L);
    }

    @Test
    void anUnreachableRedisFailsClosed() {
        LettuceConnectionFactory dead = new LettuceConnectionFactory("127.0.0.1", 6399);
        dead.setTimeout(500);
        dead.afterPropertiesSet();
        StringRedisTemplate deadTemplate = new StringRedisTemplate(dead);
        deadTemplate.afterPropertiesSet();
        try {
            var limiter = new RedisTokenBucketRateLimiter(deadTemplate, new MovableClock());

            // Not "allowed". A rate limiter that opens when its store is unreachable can be
            // switched off by anyone able to disturb the store.
            assertThatThrownBy(() -> limiter.tryConsume(freshKey(LOGIN_PER_IP)))
                    .isInstanceOf(EdgeStateUnavailableException.class);
        } finally {
            dead.destroy();
        }
    }
}
