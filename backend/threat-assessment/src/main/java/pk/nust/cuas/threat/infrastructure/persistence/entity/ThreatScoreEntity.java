package pk.nust.cuas.threat.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * JPA mapping for a {@code threat_scores} row: the current level of one track.
 *
 * <p>Mutable, unlike {@link AssessmentEntity}. This is the projection the read API answers from and
 * the row a level transition is measured against; the history it is derived from is immutable and
 * lives next door.
 */
@Entity
@Table(name = "threat_scores")
public class ThreatScoreEntity {

    @Id
    @Column(name = "track_id")
    private UUID trackId;

    @Column(name = "latest_assessment_id", nullable = false)
    private UUID latestAssessmentId;

    @Column(name = "sector_id", nullable = false)
    private String sectorId;

    @Column(name = "level")
    private String level;

    @Column(name = "composite_score", nullable = false)
    private double compositeScore;

    @Column(name = "recommendation", nullable = false)
    private String recommendation;

    @Column(name = "assessed_at", nullable = false)
    private Instant assessedAt;

    @Column(name = "observed_at", nullable = false)
    private Instant observedAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected ThreatScoreEntity() {
    }

    public ThreatScoreEntity(UUID trackId, UUID latestAssessmentId, String sectorId, String level,
                             double compositeScore, String recommendation, Instant assessedAt,
                             Instant observedAt, Instant updatedAt) {
        this.trackId = trackId;
        this.latestAssessmentId = latestAssessmentId;
        this.sectorId = sectorId;
        this.level = level;
        this.compositeScore = compositeScore;
        this.recommendation = recommendation;
        this.assessedAt = assessedAt;
        this.observedAt = observedAt;
        this.updatedAt = updatedAt;
    }

    /** Advances this projection to a newer assessment. */
    public void advanceTo(UUID assessmentId, String sectorId, String level, double compositeScore,
                          String recommendation, Instant assessedAt, Instant observedAt,
                          Instant updatedAt) {
        this.latestAssessmentId = assessmentId;
        this.sectorId = sectorId;
        this.level = level;
        this.compositeScore = compositeScore;
        this.recommendation = recommendation;
        this.assessedAt = assessedAt;
        this.observedAt = observedAt;
        this.updatedAt = updatedAt;
    }

    public UUID getTrackId() {
        return trackId;
    }

    public UUID getLatestAssessmentId() {
        return latestAssessmentId;
    }

    public String getSectorId() {
        return sectorId;
    }

    public String getLevel() {
        return level;
    }

    public double getCompositeScore() {
        return compositeScore;
    }

    public String getRecommendation() {
        return recommendation;
    }

    public Instant getAssessedAt() {
        return assessedAt;
    }

    public Instant getObservedAt() {
        return observedAt;
    }
}
