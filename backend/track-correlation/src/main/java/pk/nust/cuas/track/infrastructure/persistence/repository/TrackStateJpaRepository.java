package pk.nust.cuas.track.infrastructure.persistence.repository;

import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import pk.nust.cuas.track.infrastructure.persistence.entity.TrackStateEntity;

/** Spring Data repository for {@code track_states}. */
public interface TrackStateJpaRepository extends JpaRepository<TrackStateEntity, Long> {

    List<TrackStateEntity> findByTrackIdOrderByObservedAtDesc(UUID trackId, Pageable pageable);
}
