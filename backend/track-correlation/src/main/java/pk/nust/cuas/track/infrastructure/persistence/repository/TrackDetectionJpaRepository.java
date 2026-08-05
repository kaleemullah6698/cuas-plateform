package pk.nust.cuas.track.infrastructure.persistence.repository;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import pk.nust.cuas.track.infrastructure.persistence.entity.TrackDetectionEntity;

/** Spring Data repository for {@code track_detections}. */
public interface TrackDetectionJpaRepository extends JpaRepository<TrackDetectionEntity, Long> {

    /** The idempotency check: has this detection already been associated to any track? */
    boolean existsByDetectionId(String detectionId);

    /**
     * The distinct sensors that have fed a track — what makes a merge a <em>cross-sensor</em> one
     * (section 4.1.5). Distinct at the database rather than in memory, because a long-lived track
     * accumulates thousands of associations from a handful of sensors.
     */
    @Query("SELECT DISTINCT d.sensorId FROM TrackDetectionEntity d WHERE d.trackId = :trackId")
    List<String> findDistinctSensorIds(@Param("trackId") UUID trackId);
}
