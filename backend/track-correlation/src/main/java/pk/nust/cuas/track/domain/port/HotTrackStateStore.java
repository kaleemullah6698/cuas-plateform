package pk.nust.cuas.track.domain.port;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import pk.nust.cuas.track.domain.model.SectorId;
import pk.nust.cuas.track.domain.model.Track;

/**
 * The hot in-flight track state (ARCHITECTURE.md section 4.1.5, "Redis holds hot in-flight state").
 *
 * <p>A cache in front of the one query on the critical path: every normalised detection needs the
 * open tracks in its sector before it can be associated, which is a database round trip per detection
 * at the pipeline's highest-volume hop (section 5.8 budgets 80 ms for the whole correlation step).
 *
 * <p><b>PostgreSQL remains the source of truth.</b> This store is advisory in both directions: a miss
 * returns {@link Optional#empty()} and the caller falls back to the database, and an
 * <em>unavailable</em> store must also read as a miss rather than an error. Correlation continuing
 * without a cache is a slower system; correlation stopping because a cache is down is an
 * undetected drone. That is the same principle section 5.6 states for back-pressure — degrade
 * visibly, never drop the detection — and it is the opposite of the gateway's fail-closed rule
 * (section 7.8), because a cache miss here costs latency while a rate-limit miss there costs a
 * security control.
 *
 * <p>Declared in the domain as a port so the association pipeline never names Redis; the adapter is
 * outward (section 4.3, {@code infrastructure/cache/RedisTrackStateStore}).
 */
public interface HotTrackStateStore {

    /**
     * The cached open tracks in a sector.
     *
     * @param sector the sector to read
     * @return the tracks, or empty on a miss or when the store is unreachable — never an exception
     */
    Optional<List<Track>> openInSector(SectorId sector);

    /**
     * Seeds a sector's entry from the database, after a miss.
     *
     * @param sector the sector
     * @param tracks the open tracks the database returned
     */
    void seed(SectorId sector, List<Track> tracks);

    /**
     * Writes a track's current state through to the cache, so the next detection in its sector sees
     * it without a database read.
     *
     * @param track the track as it now stands
     */
    void put(Track track);

    /**
     * Removes a track that is no longer open — lost or merged. A terminal track is never an
     * association candidate, so leaving it cached would offer the pipeline a track it must not use.
     *
     * @param sector  the sector it was in
     * @param trackId the track to drop
     */
    void evict(SectorId sector, UUID trackId);
}
