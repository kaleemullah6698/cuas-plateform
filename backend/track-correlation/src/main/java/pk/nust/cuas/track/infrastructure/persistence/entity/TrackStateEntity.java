package pk.nust.cuas.track.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** JPA mapping for a {@code track_states} row: one observed state in a track's history. */
@Entity
@Table(name = "track_states")
public class TrackStateEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "track_id", nullable = false)
    private UUID trackId;

    @Column(name = "latitude", nullable = false)
    private double latitude;

    @Column(name = "longitude", nullable = false)
    private double longitude;

    @Column(name = "altitude_m", nullable = false)
    private double altitudeM;

    @Column(name = "speed_ms", nullable = false)
    private double speedMs;

    @Column(name = "heading_deg", nullable = false)
    private double headingDeg;

    @Column(name = "observed_at", nullable = false)
    private Instant observedAt;

    @Column(name = "detection_id", nullable = false)
    private String detectionId;

    @Column(name = "sensor_id", nullable = false)
    private String sensorId;

    @Column(name = "recorded_at", nullable = false)
    private Instant recordedAt;

    protected TrackStateEntity() {
    }

    public TrackStateEntity(UUID trackId, double latitude, double longitude, double altitudeM,
                            double speedMs, double headingDeg, Instant observedAt, String detectionId,
                            String sensorId, Instant recordedAt) {
        this.trackId = trackId;
        this.latitude = latitude;
        this.longitude = longitude;
        this.altitudeM = altitudeM;
        this.speedMs = speedMs;
        this.headingDeg = headingDeg;
        this.observedAt = observedAt;
        this.detectionId = detectionId;
        this.sensorId = sensorId;
        this.recordedAt = recordedAt;
    }

    public double latitude() {
        return latitude;
    }

    public double longitude() {
        return longitude;
    }

    public double altitudeM() {
        return altitudeM;
    }

    public double speedMs() {
        return speedMs;
    }

    public double headingDeg() {
        return headingDeg;
    }

    public Instant observedAt() {
        return observedAt;
    }

    public String detectionId() {
        return detectionId;
    }

    public String sensorId() {
        return sensorId;
    }
}
