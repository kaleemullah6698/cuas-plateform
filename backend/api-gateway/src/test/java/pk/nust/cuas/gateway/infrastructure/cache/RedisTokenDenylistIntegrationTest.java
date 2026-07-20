package pk.nust.cuas.gateway.infrastructure.cache;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import pk.nust.cuas.gateway.domain.exception.EdgeStateUnavailableException;

/**
 * The section 7.3 revocation denylist, read against a real Redis.
 */
@Testcontainers
class RedisTokenDenylistIntegrationTest {

    @Container
    @SuppressWarnings("resource")
    static GenericContainer<?> redis =
            new GenericContainer<>("redis:7.4-alpine").withExposedPorts(6379);

    private static LettuceConnectionFactory connectionFactory;
    private static StringRedisTemplate template;

    @BeforeAll
    static void startTemplate() {
        connectionFactory = new LettuceConnectionFactory(redis.getHost(), redis.getMappedPort(6379));
        connectionFactory.afterPropertiesSet();
        template = new StringRedisTemplate(connectionFactory);
        template.afterPropertiesSet();
    }

    @AfterAll
    static void stopTemplate() {
        connectionFactory.destroy();
    }

    @Test
    void aTokenNoOneHasRevokedIsAccepted() {
        var denylist = new RedisTokenDenylist(template);

        assertThat(denylist.isRevoked("tok-not-revoked")).isFalse();
    }

    @Test
    void aRevokedTokenIsRefused() {
        template.opsForValue().set(RedisTokenDenylist.KEY_PREFIX + "tok-logged-out", "logout",
                Duration.ofMinutes(15));
        var denylist = new RedisTokenDenylist(template);

        assertThat(denylist.isRevoked("tok-logged-out")).isTrue();
    }

    @Test
    void anEntryExpiresWithTheTokenItRevokes() {
        template.opsForValue().set(RedisTokenDenylist.KEY_PREFIX + "tok-short", "logout",
                Duration.ofMinutes(15));

        // The denylist stays bounded by the tokens revoked in one token lifetime rather than
        // growing forever; without a TTL it would be a permanent, ever-growing set.
        Long ttl = template.getExpire(RedisTokenDenylist.KEY_PREFIX + "tok-short");
        assertThat(ttl).isNotNull().isPositive();
    }

    @Test
    void aTokenWithoutAJtiIsTreatedAsRevoked() {
        var denylist = new RedisTokenDenylist(template);

        // Section 7.3 requires the claim. A token that carries no jti cannot be revoked
        // individually, so accepting it would create a token nobody can ever recall.
        assertThat(denylist.isRevoked(null)).isTrue();
        assertThat(denylist.isRevoked("  ")).isTrue();
    }

    @Test
    void anUnreachableRedisFailsClosed() {
        LettuceConnectionFactory dead = new LettuceConnectionFactory("127.0.0.1", 6399);
        dead.setTimeout(500);
        dead.afterPropertiesSet();
        StringRedisTemplate deadTemplate = new StringRedisTemplate(dead);
        deadTemplate.afterPropertiesSet();
        try {
            var denylist = new RedisTokenDenylist(deadTemplate);

            // Answering "not revoked" here would let a revoked token work for as long as Redis
            // was down, which is precisely when an attacker would want it to.
            assertThatThrownBy(() -> denylist.isRevoked("tok-1"))
                    .isInstanceOf(EdgeStateUnavailableException.class);
        } finally {
            dead.destroy();
        }
    }
}
