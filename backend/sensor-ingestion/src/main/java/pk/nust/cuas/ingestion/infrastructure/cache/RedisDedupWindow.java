package pk.nust.cuas.ingestion.infrastructure.cache;

import java.time.Duration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import pk.nust.cuas.ingestion.config.IngestionProperties;
import pk.nust.cuas.ingestion.domain.port.DedupWindow;

/**
 * Redis implementation of the {@link DedupWindow} (ARCHITECTURE.md section 4.1.4).
 *
 * <p>Each {@code (sensorId, sensorSeq)} pair is one key with a TTL that bounds the memory the
 * window costs. The mark is a plain {@code SET} with expiry; the check is an existence test. They
 * are kept separate so the mark can be applied only after a successful publish (see
 * {@link DedupWindow}). Redis rather than an in-process set because ingestion scales horizontally
 * by partition — an in-process window would let the same detection through once per instance after
 * a partition rebalance.
 */
@Component
public class RedisDedupWindow implements DedupWindow {

    private static final String KEY_PREFIX = "cuas:sensor:dedup:";
    private static final String SEEN = "1";

    private final StringRedisTemplate redis;
    private final Duration ttl;

    public RedisDedupWindow(StringRedisTemplate redis, IngestionProperties properties) {
        this.redis = redis;
        this.ttl = properties.dedup().ttl();
    }

    @Override
    public boolean seenBefore(String sensorId, long sensorSeq) {
        return Boolean.TRUE.equals(redis.hasKey(key(sensorId, sensorSeq)));
    }

    @Override
    public void markSeen(String sensorId, long sensorSeq) {
        redis.opsForValue().set(key(sensorId, sensorSeq), SEEN, ttl);
    }

    private String key(String sensorId, long sensorSeq) {
        return KEY_PREFIX + sensorId + ':' + sensorSeq;
    }
}
