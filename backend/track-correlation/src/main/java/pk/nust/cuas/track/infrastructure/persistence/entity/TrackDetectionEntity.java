package pk.nust.cuas.track.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * JPA mapping for a {@code track_detections} row: the record that a detection was associated to a
 * track. The unique {@code (track_id, detection_id)} constraint makes association idempotent.
 */
@Entity
@Table(name = "track_detections")
public class TrackDetectionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "track_id", nullable = false)
    private UUID trackId;

    @Column(name = "detection_id", nullable = false)
    private String detectionId;

    @Column(name = "sensor_id", nullable = false)
    private String sensorId;

    @Column(name = "sensor_seq", nullable = false)
    private long sensorSeq;

    @Column(name = "distance_m")
    private Double distanceM;

    @Column(name = "associated_at", nullable = false)
    private Instant associatedAt;

    protected TrackDetectionEntity() {
    }

    public TrackDetectionEntity(UUID trackId, String detectionId, String sensorId, long sensorSeq,
                                Double distanceM, Instant associatedAt) {
        this.trackId = trackId;
        this.detectionId = detectionId;
        this.sensorId = sensorId;
        this.sensorSeq = sensorSeq;
        this.distanceM = distanceM;
        this.associatedAt = associatedAt;
    }
}
