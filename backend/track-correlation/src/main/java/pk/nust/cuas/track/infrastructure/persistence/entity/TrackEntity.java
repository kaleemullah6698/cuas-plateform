package pk.nust.cuas.track.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;

/**
 * JPA mapping for the {@code tracks} row — a track's current state. A separate type from the domain
 * {@code Track}, mapped explicitly (CLAUDE.md section 2). Carries a {@code @Version} for optimistic
 * locking; the foundation processes single-threaded, but the guard costs nothing and is correct when
 * M5.2 parallelises per sector.
 */
@Entity
@Table(name = "tracks")
public class TrackEntity {

    @Id
    @Column(name = "track_id")
    private UUID trackId;

    @Column(name = "sector_id", nullable = false)
    private String sectorId;

    @Column(name = "status", nullable = false)
    private String status;

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

    @Column(name = "last_detection_id", nullable = false)
    private String lastDetectionId;

    @Column(name = "last_sensor_id", nullable = false)
    private String lastSensorId;

    @Column(name = "detection_count", nullable = false)
    private int detectionCount;

    @Column(name = "first_seen", nullable = false)
    private Instant firstSeen;

    @Column(name = "last_seen", nullable = false)
    private Instant lastSeen;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    protected TrackEntity() {
    }

    public TrackEntity(UUID trackId, String sectorId, String status, double latitude, double longitude,
                       double altitudeM, double speedMs, double headingDeg, String lastDetectionId,
                       String lastSensorId, int detectionCount, Instant firstSeen, Instant lastSeen) {
        this.trackId = trackId;
        this.sectorId = sectorId;
        this.status = status;
        this.latitude = latitude;
        this.longitude = longitude;
        this.altitudeM = altitudeM;
        this.speedMs = speedMs;
        this.headingDeg = headingDeg;
        this.lastDetectionId = lastDetectionId;
        this.lastSensorId = lastSensorId;
        this.detectionCount = detectionCount;
        this.firstSeen = firstSeen;
        this.lastSeen = lastSeen;
    }

    /** Applies an advanced state to an existing track row. */
    public void applyState(String status, double latitude, double longitude, double altitudeM,
                           double speedMs, double headingDeg, String lastDetectionId, String lastSensorId,
                           int detectionCount, Instant lastSeen) {
        this.status = status;
        this.latitude = latitude;
        this.longitude = longitude;
        this.altitudeM = altitudeM;
        this.speedMs = speedMs;
        this.headingDeg = headingDeg;
        this.lastDetectionId = lastDetectionId;
        this.lastSensorId = lastSensorId;
        this.detectionCount = detectionCount;
        this.lastSeen = lastSeen;
    }

    public UUID trackId() {
        return trackId;
    }

    public String sectorId() {
        return sectorId;
    }

    public String status() {
        return status;
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

    public String lastDetectionId() {
        return lastDetectionId;
    }

    public String lastSensorId() {
        return lastSensorId;
    }

    public int detectionCount() {
        return detectionCount;
    }

    public Instant firstSeen() {
        return firstSeen;
    }

    public Instant lastSeen() {
        return lastSeen;
    }
}
