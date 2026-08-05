package pk.nust.cuas.track.infrastructure.persistence.repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import pk.nust.cuas.track.infrastructure.persistence.entity.TrackEntity;

/** Spring Data repository for {@code tracks}. */
public interface TrackJpaRepository extends JpaRepository<TrackEntity, UUID> {

    /** Association candidates: open (non-lost) tracks in a sector last fed no earlier than {@code notBefore}. */
    @Query("SELECT t FROM TrackEntity t WHERE t.sectorId = :sector"
            + " AND t.lastDetectionAt >= :notBefore AND t.status <> 'LOST'")
    List<TrackEntity> findOpenInSector(@Param("sector") String sector,
                                       @Param("notBefore") Instant notBefore);

    /** Open (non-lost) tracks last fed before {@code threshold} — candidates for a timeout transition. */
    @Query("SELECT t FROM TrackEntity t WHERE t.lastDetectionAt < :threshold AND t.status <> 'LOST'")
    List<TrackEntity> findOpenStaleBefore(@Param("threshold") Instant threshold);

    List<TrackEntity> findBySectorId(String sectorId, Pageable pageable);

    List<TrackEntity> findByStatus(String status, Pageable pageable);

    List<TrackEntity> findBySectorIdAndStatus(String sectorId, String status, Pageable pageable);
}
