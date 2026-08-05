package pk.nust.cuas.track.infrastructure.cache;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import pk.nust.cuas.track.domain.model.SectorId;
import pk.nust.cuas.track.domain.model.Track;
import pk.nust.cuas.track.domain.port.HotTrackStateStore;

/**
 * The hot-state store for a deployment without Redis: every read is a miss, every write is discarded.
 *
 * <p>Not a test double — this is the configuration that proves the cache is genuinely optional. The
 * {@link HotTrackStateStore} contract is written so a permanent miss is correct behaviour, and that
 * claim is only worth anything if something exercises it. Correlation runs identically here, reading
 * candidates from PostgreSQL every time: slower, never wrong.
 */
public class NoHotTrackStateStore implements HotTrackStateStore {

    @Override
    public Optional<List<Track>> openInSector(SectorId sector) {
        return Optional.empty();
    }

    @Override
    public void seed(SectorId sector, List<Track> tracks) {
        // Nothing is cached, so there is nothing to seed.
    }

    @Override
    public void put(Track track) {
        // Nothing is cached, so there is nothing to write through.
    }

    @Override
    public void evict(SectorId sector, UUID trackId) {
        // Nothing is cached, so there is nothing to evict.
    }
}
