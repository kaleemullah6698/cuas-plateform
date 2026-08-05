package pk.nust.cuas.track.infrastructure.cache;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import pk.nust.cuas.track.domain.model.SectorId;
import pk.nust.cuas.track.domain.model.Track;
import pk.nust.cuas.track.domain.port.HotTrackStateStore;

/**
 * Redis adapter for the hot in-flight track state (ARCHITECTURE.md sections 4.1.5, 4.3).
 *
 * <p>One Redis hash per sector, fields keyed by track id. A hash rather than a serialised list,
 * because writes then touch one field instead of rewriting the sector: a detection updates one track,
 * and a list would make every update an O(sector) rewrite and a lost-update race between the
 * detection consumer and the two sweeps.
 *
 * <p><b>Every operation swallows Redis failures.</b> A read failure returns a miss and the caller
 * falls back to PostgreSQL; a write failure is logged and dropped, leaving the entry stale — which
 * the TTL then reaps. This is deliberate and is the opposite of the gateway's fail-closed rule
 * (section 7.8): there, an unreachable Redis means a security control is not being enforced, so the
 * request must be refused. Here it means a query is slower. Refusing to correlate detections because
 * a cache is down would turn a degraded system into an undetected drone, which section 5.6's
 * governing principle forbids.
 *
 * <p>The TTL is the safety net that keeps a cache bug from becoming a correctness bug. Set from the
 * lost timeout, it guarantees any stale entry expires within the window in which a track could still
 * have been an association candidate at all.
 */
public class RedisTrackStateStore implements HotTrackStateStore {

    private static final Logger log = LoggerFactory.getLogger(RedisTrackStateStore.class);
    private static final String KEY_PREFIX = "cuas:track:sector:";

    private final StringRedisTemplate redis;
    private final Duration ttl;

    public RedisTrackStateStore(StringRedisTemplate redis, Duration ttl) {
        this.redis = redis;
        this.ttl = ttl;
    }

    @Override
    public Optional<List<Track>> openInSector(SectorId sector) {
        try {
            Map<Object, Object> entries = redis.opsForHash().entries(key(sector));
            if (entries.isEmpty()) {
                return Optional.empty();
            }
            List<Track> tracks = new ArrayList<>(entries.size());
            for (Object value : entries.values()) {
                // An unreadable entry is a miss for the whole sector, not a silently short list: a
                // partial candidate set would let a detection open a duplicate track rather than
                // associate to the one it could not read.
                tracks.add(TrackCodec.decode(value.toString()));
            }
            return Optional.of(tracks);
        } catch (DataAccessException | IllegalArgumentException ex) {
            log.debug("Hot-state read for sector {} failed; falling back to the database",
                    sector.value(), ex);
            return Optional.empty();
        }
    }

    @Override
    public void seed(SectorId sector, List<Track> tracks) {
        if (tracks.isEmpty()) {
            return;
        }
        try {
            HashOperations<String, Object, Object> hash = redis.opsForHash();
            Map<String, String> entries = new java.util.HashMap<>();
            for (Track track : tracks) {
                entries.put(track.id().toString(), TrackCodec.encode(track));
            }
            String key = key(sector);
            hash.putAll(key, entries);
            redis.expire(key, ttl);
        } catch (DataAccessException ex) {
            log.debug("Hot-state seed for sector {} failed; the next read simply misses again",
                    sector.value(), ex);
        }
    }

    @Override
    public void put(Track track) {
        try {
            String key = key(track.sector());
            redis.opsForHash().put(key, track.id().toString(), TrackCodec.encode(track));
            redis.expire(key, ttl);
        } catch (DataAccessException ex) {
            log.debug("Hot-state write for track {} failed; the entry stays stale until its TTL",
                    track.id(), ex);
        }
    }

    @Override
    public void evict(SectorId sector, UUID trackId) {
        try {
            redis.opsForHash().delete(key(sector), trackId.toString());
        } catch (DataAccessException ex) {
            log.debug("Hot-state evict of track {} failed; its TTL will reap it", trackId, ex);
        }
    }

    private static String key(SectorId sector) {
        return KEY_PREFIX + sector.value();
    }
}
