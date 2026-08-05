package pk.nust.cuas.track.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;
import pk.nust.cuas.track.domain.port.HotTrackStateStore;
import pk.nust.cuas.track.infrastructure.cache.NoHotTrackStateStore;
import pk.nust.cuas.track.infrastructure.cache.RedisTrackStateStore;

/**
 * Wires the hot in-flight track state (ARCHITECTURE.md section 4.1.5).
 *
 * <p>Two adapters behind one port, chosen by {@code correlation.hot-state.enabled}. Redis when it is
 * on; a no-op store — every read a miss, every write discarded — when it is off.
 *
 * <p>The no-op adapter is not a test double. It is what makes the cache genuinely optional: the port
 * is defined so that a permanent miss is correct behaviour, so a deployment without Redis is a
 * slower correct system rather than a broken one, and that claim is only credible if there is a
 * configuration in which it is exercised. It also keeps the alternative to Redis out of the
 * application layer, which never learns which adapter it has.
 */
@Configuration
public class HotStateConfig {

    @Bean
    @ConditionalOnProperty(name = "correlation.hot-state.enabled", havingValue = "true",
            matchIfMissing = true)
    public HotTrackStateStore redisTrackStateStore(StringRedisTemplate redis,
                                                   CorrelationProperties properties) {
        // The TTL is the lost timeout: past it a track could not have been an association candidate
        // anyway, so no stale entry can outlive its usefulness even if an evict is lost.
        return new RedisTrackStateStore(redis, properties.lifecycle().lostTimeout());
    }

    @Bean
    @ConditionalOnProperty(name = "correlation.hot-state.enabled", havingValue = "false")
    public HotTrackStateStore noHotTrackStateStore() {
        return new NoHotTrackStateStore();
    }
}
