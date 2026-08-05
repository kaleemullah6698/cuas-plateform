package pk.nust.cuas.track.infrastructure.persistence.repository;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import pk.nust.cuas.track.infrastructure.persistence.entity.MergeCandidateEntity;
import pk.nust.cuas.track.infrastructure.persistence.entity.MergeCandidateId;

/** Spring Data repository for {@code track_merge_candidates}. */
public interface MergeCandidateJpaRepository
        extends JpaRepository<MergeCandidateEntity, MergeCandidateId> {

    /**
     * Deletes every candidacy a track is part of, on either side of the pair. Used when a track
     * leaves {@code CONFIRMED}, so stale evidence cannot make a later pairing merge early.
     */
    @Modifying
    @Query("DELETE FROM MergeCandidateEntity c"
            + " WHERE c.id.lowerTrackId = :trackId OR c.id.higherTrackId = :trackId")
    void deleteAllInvolving(@Param("trackId") UUID trackId);
}
