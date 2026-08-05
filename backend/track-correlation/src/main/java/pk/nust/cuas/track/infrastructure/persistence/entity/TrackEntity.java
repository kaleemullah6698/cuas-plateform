package pk.nust.cuas.track.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;
import pk.nust.cuas.track.domain.model.TrackStatus;

/**
 * JPA mapping for the {@code tracks} row — a track's current estimated state and its serialised
 * Kalman filter. A separate type from the domain {@code Track}, mapped explicitly (CLAUDE.md section
 * 2). {@code last_seen} is the last-detection time (what the miss timeouts count from);
 * {@code observed_at} is the time the estimate is valid for, which a coasting prediction advances
 * past {@code last_seen}. Carries a {@code @Version} for optimistic locking, so the timeout sweep and
 * a concurrent detection cannot silently overwrite each other.
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

    @Column(name = "observed_at", nullable = false)
    private Instant observedAt;

    @Column(name = "last_seen", nullable = false)
    private Instant lastDetectionAt;

    @Column(name = "filter_state", nullable = false)
    private String filterState;

    @Column(name = "merged_into_track_id")
    private UUID mergedIntoTrackId;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    protected TrackEntity() {
    }

    public TrackEntity(UUID trackId, String sectorId, String status, double latitude, double longitude,
                       double altitudeM, double speedMs, double headingDeg, String lastDetectionId,
                       String lastSensorId, int detectionCount, Instant firstSeen, Instant observedAt,
                       Instant lastDetectionAt, String filterState) {
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
        this.observedAt = observedAt;
        this.lastDetectionAt = lastDetectionAt;
        this.filterState = filterState;
    }

    /** Applies an advanced estimate (from an association or a coast) to the row. */
    public void applyEstimate(String status, double latitude, double longitude, double altitudeM,
                              double speedMs, double headingDeg, String lastDetectionId, String lastSensorId,
                              int detectionCount, Instant observedAt, Instant lastDetectionAt,
                              String filterState) {
        this.status = status;
        this.latitude = latitude;
        this.longitude = longitude;
        this.altitudeM = altitudeM;
        this.speedMs = speedMs;
        this.headingDeg = headingDeg;
        this.lastDetectionId = lastDetectionId;
        this.lastSensorId = lastSensorId;
        this.detectionCount = detectionCount;
        this.observedAt = observedAt;
        this.lastDetectionAt = lastDetectionAt;
        this.filterState = filterState;
    }

    /** Applies the terminal lost status, leaving the estimate as the last known one. */
    public void markLost() {
        this.status = TrackStatus.LOST.name();
    }

    /**
     * Applies the terminal merged status and the link to the survivor, leaving the estimate as the
     * last one this track believed. The survivor holds the live answer.
     */
    public void markMerged(UUID survivorTrackId) {
        this.status = TrackStatus.MERGED.name();
        this.mergedIntoTrackId = survivorTrackId;
    }

    public UUID mergedIntoTrackId() {
        return mergedIntoTrackId;
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

    public Instant observedAt() {
        return observedAt;
    }

    public Instant lastDetectionAt() {
        return lastDetectionAt;
    }

    public String filterState() {
        return filterState;
    }
}
