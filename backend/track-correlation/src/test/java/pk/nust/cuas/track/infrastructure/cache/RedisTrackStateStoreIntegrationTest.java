package pk.nust.cuas.track.infrastructure.cache;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
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
import pk.nust.cuas.track.domain.model.GeoPoint;
import pk.nust.cuas.track.domain.model.KalmanState;
import pk.nust.cuas.track.domain.model.SectorId;
import pk.nust.cuas.track.domain.model.Track;
import pk.nust.cuas.track.domain.model.TrackState;
import pk.nust.cuas.track.domain.model.TrackStatus;
import pk.nust.cuas.track.domain.model.Velocity;
import pk.nust.cuas.track.domain.service.KalmanFilter;

/**
 * The hot-state adapter against a real Redis (ARCHITECTURE.md section 4.1.5).
 *
 * <p>Against a real server rather than an embedded fake, because the two behaviours that matter —
 * that a hash field round-trips a full Kalman state byte-for-byte, and that a TTL actually expires
 * the key — are exactly what a fake would get approximately right.
 */
@Testcontainers
class RedisTrackStateStoreIntegrationTest {

    private static final Instant T = Instant.parse("2026-08-04T12:00:00Z");
    private static final SectorId SECTOR = new SectorId("SECTOR_33_73");
    private static final GeoPoint HERE = new GeoPoint(33.6844, 73.0479, 120.5);

    @Container
    @SuppressWarnings("resource")
    static GenericContainer<?> redisContainer =
            new GenericContainer<>("redis:7.4-alpine").withExposedPorts(6379);

    private static LettuceConnectionFactory connectionFactory;
    private static StringRedisTemplate redis;

    private final KalmanFilter kalman = new KalmanFilter(1.0, 25.0, 100, 100, 10);

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

    private RedisTrackStateStore store() {
        return new RedisTrackStateStore(redis, Duration.ofSeconds(30));
    }

    private Track track(UUID id, TrackStatus status) {
        KalmanState filter = kalman.step(
                kalman.initial(HERE, new Velocity(22.5, 137.25)), 3.5, new GeoPoint(33.685, 73.048, 121.0));
        TrackState estimate = new TrackState(kalman.position(filter), kalman.velocity(filter),
                T, "detection-" + id, "radar-1");
        return Track.reconstitute(id, SECTOR, status, filter, estimate, 7, T.minusSeconds(60), T);
    }

    @Test
    void anEmptySectorIsAMissNotAnEmptyList() {
        // The distinction matters: empty-list would tell the caller "no candidates" and let it open
        // a duplicate track, where a miss sends it to the database for the real answer.
        assertThat(store().openInSector(SECTOR)).isEmpty();
    }

    @Test
    void aTrackRoundTripsWithItsFilterStateIntact() {
        Track original = track(UUID.randomUUID(), TrackStatus.CONFIRMED);
        RedisTrackStateStore store = store();

        store.put(original);
        Optional<List<Track>> cached = store.openInSector(SECTOR);

        assertThat(cached).isPresent();
        assertThat(cached.get()).hasSize(1);
        Track read = cached.get().get(0);
        assertThat(read.id()).isEqualTo(original.id());
        assertThat(read.sector()).isEqualTo(original.sector());
        assertThat(read.status()).isEqualTo(original.status());
        assertThat(read.detectionCount()).isEqualTo(original.detectionCount());
        assertThat(read.firstSeen()).isEqualTo(original.firstSeen());
        assertThat(read.lastDetectionAt()).isEqualTo(original.lastDetectionAt());
        assertThat(read.estimate()).isEqualTo(original.estimate());
        // The filter must resume exactly: a cached track that restarted its covariance would
        // silently retune the tracker.
        assertThat(read.filter()).isEqualTo(original.filter());
    }

    @Test
    void aMergedTrackKeepsItsSurvivorLinkThroughTheCache() {
        UUID survivor = UUID.randomUUID();
        Track absorbed = track(UUID.randomUUID(), TrackStatus.CONFIRMED)
                .mergeInto(track(survivor, TrackStatus.CONFIRMED), T).track();
        RedisTrackStateStore store = store();

        store.put(absorbed);

        Track read = store.openInSector(SECTOR).orElseThrow().get(0);
        assertThat(read.status()).isEqualTo(TrackStatus.MERGED);
        assertThat(read.mergedInto()).contains(survivor);
    }

    @Test
    void seedingPopulatesTheWholeSectorAndPutUpdatesOneTrack() {
        Track a = track(UUID.randomUUID(), TrackStatus.CONFIRMED);
        Track b = track(UUID.randomUUID(), TrackStatus.TENTATIVE);
        RedisTrackStateStore store = store();

        store.seed(SECTOR, List.of(a, b));
        assertThat(store.openInSector(SECTOR).orElseThrow()).hasSize(2);

        // A write touches one field, leaving the rest of the sector alone.
        store.put(a);
        assertThat(store.openInSector(SECTOR).orElseThrow()).hasSize(2);
    }

    @Test
    void evictingRemovesOneTrackAndLeavesTheSector() {
        Track a = track(UUID.randomUUID(), TrackStatus.CONFIRMED);
        Track b = track(UUID.randomUUID(), TrackStatus.CONFIRMED);
        RedisTrackStateStore store = store();
        store.seed(SECTOR, List.of(a, b));

        store.evict(SECTOR, a.id());

        List<Track> remaining = store.openInSector(SECTOR).orElseThrow();
        assertThat(remaining).hasSize(1);
        assertThat(remaining.get(0).id()).isEqualTo(b.id());
    }

    @Test
    void seedingAnEmptySectorWritesNothing() {
        store().seed(SECTOR, List.of());

        assertThat(redis.hasKey("cuas:track:sector:" + SECTOR.value())).isFalse();
    }

    @Test
    void anEntryCarriesTheConfiguredTtl() {
        // The TTL is what stops a lost evict from becoming a permanently stale candidate.
        new RedisTrackStateStore(redis, Duration.ofSeconds(30))
                .put(track(UUID.randomUUID(), TrackStatus.CONFIRMED));

        Long ttl = redis.getExpire("cuas:track:sector:" + SECTOR.value());
        assertThat(ttl).isNotNull().isBetween(1L, 30L);
    }

    @Test
    void anUnreadableEntryIsAMissRatherThanACorruptTrack() {
        // A format change deployed mid-flight must degrade to database reads, not to wrong tracks.
        redis.opsForHash().put("cuas:track:sector:" + SECTOR.value(),
                UUID.randomUUID().toString(), "not-a-v1-record");

        assertThat(store().openInSector(SECTOR)).isEmpty();
    }
}
