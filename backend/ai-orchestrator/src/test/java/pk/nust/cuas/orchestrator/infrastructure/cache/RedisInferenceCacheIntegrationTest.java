package pk.nust.cuas.orchestrator.infrastructure.cache;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.Optional;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import pk.nust.cuas.inference.v1.ComputeDevice;
import pk.nust.cuas.inference.v1.ConfidenceReport;
import pk.nust.cuas.inference.v1.InferenceResponse;
import pk.nust.cuas.inference.v1.InferenceStatus;
import pk.nust.cuas.inference.v1.Prediction;
import pk.nust.cuas.orchestrator.domain.model.ServiceId;

/**
 * The inference cache against a real Redis (ARCHITECTURE.md section 4.1.6).
 *
 * <p>Against a real server rather than an in-memory fake, because the two behaviours that matter —
 * that a full protobuf response round-trips byte-identically, and that a TTL actually expires the key
 * — are exactly what a fake approximates.
 */
@Testcontainers
class RedisInferenceCacheIntegrationTest {

    private static final ServiceId SERVICE = new ServiceId("some-service");
    private static final String KEY = "a".repeat(64);

    @Container
    @SuppressWarnings("resource")
    static GenericContainer<?> redisContainer =
            new GenericContainer<>("redis:7.4-alpine").withExposedPorts(6379);

    private static LettuceConnectionFactory connectionFactory;
    private static StringRedisTemplate redis;

    @BeforeAll
    static void connect() {
        connectionFactory = new LettuceConnectionFactory(new RedisStandaloneConfiguration(
                redisContainer.getHost(), redisContainer.getMappedPort(6379)));
        connectionFactory.afterPropertiesSet();
        redis = new StringRedisTemplate(connectionFactory);
        redis.afterPropertiesSet();
    }

    @AfterAll
    static void disconnect() {
        connectionFactory.destroy();
    }

    @BeforeEach
    void clear() {
        redis.getConnectionFactory().getConnection().serverCommands().flushDb();
    }

    private RedisInferenceCache cache() {
        return new RedisInferenceCache(redis, Duration.ofSeconds(60));
    }

    private static InferenceResponse response() {
        return InferenceResponse.newBuilder()
                .setRequestId("req-1")
                .setTrackId("track-1")
                .setStatus(InferenceStatus.STATUS_OK)
                .addPredictions(Prediction.newBuilder()
                        .setLabel("quadcopter-small").setLabelClass("DRONE")
                        .setConfidence(0.9412f).setRank(1)
                        .putAttributes("range_m", 640.5f))
                .setConfidence(ConfidenceReport.newBuilder()
                        .setScore(0.9412f).setCalibratedScore(0.9312f)
                        .setEntropy(0.2134f).setMethod("temperature_scaled").setIsReliable(true))
                .setModelId("some-service")
                .setModelVersion("1.3.0")
                .setModelSha("abc123")
                .setInferenceMs(43)
                .setDevice(ComputeDevice.DEVICE_CUDA)
                .build();
    }

    @Test
    void anAbsentKeyIsAMiss() {
        assertThat(cache().get(SERVICE, KEY)).isEmpty();
    }

    @Test
    void aResponseRoundTripsByteIdentically() {
        // A cache hit must be indistinguishable from a fresh call; a lossy encoding would silently
        // change what downstream scores.
        InferenceResponse original = response();
        RedisInferenceCache cache = cache();

        cache.put(SERVICE, KEY, original);
        Optional<InferenceResponse> cached = cache.get(SERVICE, KEY);

        assertThat(cached).isPresent();
        assertThat(cached.get()).isEqualTo(original);
        assertThat(cached.get().toByteArray()).isEqualTo(original.toByteArray());
    }

    @Test
    void theServiceIsPartOfTheKey() {
        // The same input asked of two services is two different questions.
        RedisInferenceCache cache = cache();
        cache.put(SERVICE, KEY, response());

        assertThat(cache.get(new ServiceId("another-service"), KEY)).isEmpty();
        assertThat(cache.get(SERVICE, KEY)).isPresent();
    }

    @Test
    void anEntryCarriesTheConfiguredTtl() {
        cache().put(SERVICE, KEY, response());

        Long ttl = redis.getExpire("cuas:ai:inference:" + SERVICE.value() + ':' + KEY);
        assertThat(ttl).isNotNull().isBetween(1L, 60L);
    }

    @Test
    void aShortTtlExpiresTheEntry() throws InterruptedException {
        RedisInferenceCache shortLived = new RedisInferenceCache(redis, Duration.ofSeconds(1));
        shortLived.put(SERVICE, KEY, response());
        assertThat(shortLived.get(SERVICE, KEY)).isPresent();

        Thread.sleep(1500);

        assertThat(shortLived.get(SERVICE, KEY))
                .as("a stale classification must not outlive the situation that produced it")
                .isEmpty();
    }

    @Test
    void anUnreadableEntryIsAMissRatherThanAnError() {
        // A contract change deployed mid-flight degrades to real inference, not a corrupt result.
        redis.opsForValue().set("cuas:ai:inference:" + SERVICE.value() + ':' + KEY, "not-base64!!");

        assertThat(cache().get(SERVICE, KEY)).isEmpty();
    }

    @Test
    void anUnreachableRedisReadsAsAMissRatherThanThrowing() {
        // Section 5.6: a cache outage must cost latency, never correctness.
        LettuceConnectionFactory dead = new LettuceConnectionFactory(
                new RedisStandaloneConfiguration("localhost", 6));
        dead.afterPropertiesSet();
        StringRedisTemplate deadTemplate = new StringRedisTemplate(dead);
        deadTemplate.afterPropertiesSet();
        RedisInferenceCache cache = new RedisInferenceCache(deadTemplate, Duration.ofSeconds(60));

        assertThat(cache.get(SERVICE, KEY)).isEmpty();
        cache.put(SERVICE, KEY, response());   // must not throw

        dead.destroy();
    }
}
