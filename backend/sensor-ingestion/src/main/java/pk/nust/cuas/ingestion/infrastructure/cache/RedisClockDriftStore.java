package pk.nust.cuas.ingestion.infrastructure.cache;

import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import pk.nust.cuas.ingestion.config.IngestionProperties;
import pk.nust.cuas.ingestion.domain.port.ClockDriftStore;

/**
 * Redis implementation of the {@link ClockDriftStore}.
 *
 * <p>The running drift estimate for a sensor is a single string key with a TTL: it survives a
 * partition rebalance so a sensor that moves between instances keeps its warmed-up estimate, and it
 * expires if the sensor stops reporting so dead sensors do not accumulate keys forever. A corrupt
 * value (which should never occur) is treated as "no estimate yet" rather than failing the
 * detection — losing the smoothing is far cheaper than dropping the record.
 */
@Component
public class RedisClockDriftStore implements ClockDriftStore {

    private static final String KEY_PREFIX = "cuas:sensor:drift:";

    private static final Logger log = LoggerFactory.getLogger(RedisClockDriftStore.class);

    private final StringRedisTemplate redis;
    private final Duration ttl;

    public RedisClockDriftStore(StringRedisTemplate redis, IngestionProperties properties) {
        this.redis = redis;
        this.ttl = properties.drift().ttl();
    }

    @Override
    public long currentDriftMs(String sensorId) {
        String value = redis.opsForValue().get(KEY_PREFIX + sensorId);
        if (value == null) {
            return 0L;
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException ex) {
            log.warn("Corrupt drift value '{}' for sensor '{}'; treating as no estimate", value, sensorId);
            return 0L;
        }
    }

    @Override
    public void update(String sensorId, long driftMs) {
        redis.opsForValue().set(KEY_PREFIX + sensorId, Long.toString(driftMs), ttl);
    }
}
