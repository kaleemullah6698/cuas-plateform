package pk.nust.cuas.orchestrator.infrastructure.cache;

import com.google.protobuf.InvalidProtocolBufferException;
import java.time.Duration;
import java.util.Base64;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import pk.nust.cuas.inference.v1.InferenceResponse;
import pk.nust.cuas.orchestrator.application.port.InferenceCache;
import pk.nust.cuas.orchestrator.domain.model.ServiceId;

/**
 * Redis adapter for the inference cache (ARCHITECTURE.md section 4.1.6, TTL 60 s).
 *
 * <p>Stores the serialised {@code InferenceResponse}, Base64-encoded so it survives a string-valued
 * Redis template. Protobuf rather than JSON: it is the same wire format the response already has, so
 * a cached answer is byte-identical to a fresh one — no field is lost or reinterpreted on the way
 * through, which is what makes a cache hit genuinely indistinguishable from a call.
 *
 * <p><b>Every failure is swallowed.</b> A read failure returns a miss and the caller infers for real;
 * a write failure is logged and dropped. This mirrors the track hot-state store and rests on the same
 * §5.6 principle: a cache outage must cost latency, never correctness. It is the deliberate opposite
 * of the gateway's fail-closed rule (§7.8), where an unreachable Redis means a security control is
 * not being enforced.
 */
public class RedisInferenceCache implements InferenceCache {

    private static final Logger log = LoggerFactory.getLogger(RedisInferenceCache.class);
    private static final String KEY_PREFIX = "cuas:ai:inference:";

    private final StringRedisTemplate redis;
    private final Duration ttl;

    public RedisInferenceCache(StringRedisTemplate redis, Duration ttl) {
        this.redis = redis;
        this.ttl = ttl;
    }

    @Override
    public Optional<InferenceResponse> get(ServiceId service, String key) {
        try {
            String cached = redis.opsForValue().get(key(service, key));
            if (cached == null) {
                return Optional.empty();
            }
            return Optional.of(InferenceResponse.parseFrom(Base64.getDecoder().decode(cached)));
        } catch (DataAccessException | InvalidProtocolBufferException | IllegalArgumentException ex) {
            // An unreadable entry is a miss, not an error: a contract change deployed mid-flight
            // degrades to real inference rather than to a corrupt result.
            log.debug("Inference cache read failed for {}; inferring for real", service, ex);
            return Optional.empty();
        }
    }

    @Override
    public void put(ServiceId service, String key, InferenceResponse response) {
        try {
            redis.opsForValue().set(key(service, key),
                    Base64.getEncoder().encodeToString(response.toByteArray()), ttl);
        } catch (DataAccessException ex) {
            log.debug("Inference cache write failed for {}; the next identical call simply misses",
                    service, ex);
        }
    }

    /** The service is part of the key: the same input asked of two services is two questions. */
    private static String key(ServiceId service, String contentHash) {
        return KEY_PREFIX + service.value() + ':' + contentHash;
    }
}
