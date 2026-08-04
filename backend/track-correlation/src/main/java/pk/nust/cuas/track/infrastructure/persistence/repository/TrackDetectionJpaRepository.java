package pk.nust.cuas.track.infrastructure.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import pk.nust.cuas.track.infrastructure.persistence.entity.TrackDetectionEntity;

/** Spring Data repository for {@code track_detections}. */
public interface TrackDetectionJpaRepository extends JpaRepository<TrackDetectionEntity, Long> {

    /** The idempotency check: has this detection already been associated to any track? */
    boolean existsByDetectionId(String detectionId);
}
