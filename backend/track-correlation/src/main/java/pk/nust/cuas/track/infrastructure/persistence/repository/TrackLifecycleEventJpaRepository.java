package pk.nust.cuas.track.infrastructure.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import pk.nust.cuas.track.infrastructure.persistence.entity.TrackLifecycleEventEntity;

/** Spring Data repository for {@code track_lifecycle_events} (insert-only). */
public interface TrackLifecycleEventJpaRepository extends JpaRepository<TrackLifecycleEventEntity, Long> {
}
