package pk.nust.cuas.track.infrastructure.persistence.repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import pk.nust.cuas.track.infrastructure.persistence.entity.TrackEntity;

/** Spring Data repository for {@code tracks}. */
public interface TrackJpaRepository extends JpaRepository<TrackEntity, UUID> {

    /** Association candidates: open tracks in a sector last seen no earlier than {@code notBefore}. */
    List<TrackEntity> findBySectorIdAndLastSeenGreaterThanEqual(String sectorId, Instant notBefore);

    List<TrackEntity> findBySectorId(String sectorId, Pageable pageable);

    List<TrackEntity> findByStatus(String status, Pageable pageable);

    List<TrackEntity> findBySectorIdAndStatus(String sectorId, String status, Pageable pageable);
}
