package pk.nust.cuas.threat.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** JPA mapping for an {@code assessments} row: one immutable score, with its inputs and policy. */
@Entity
@Table(name = "assessments")
public class AssessmentEntity {

    @Id
    @Column(name = "assessment_id")
    private UUID assessmentId;

    @Column(name = "track_id", nullable = false)
    private UUID trackId;

    @Column(name = "sector_id", nullable = false)
    private String sectorId;

    @Column(name = "composite_score", nullable = false)
    private double compositeScore;

    /** Null means unassessed, never LOW — see the migration's comment on this column. */
    @Column(name = "level")
    private String level;

    @Column(name = "recommendation", nullable = false)
    private String recommendation;

    @Column(name = "rule_id", nullable = false)
    private String ruleId;

    @Column(name = "rationale", nullable = false)
    private String rationale;

    @Column(name = "policy_id", nullable = false)
    private String policyId;

    @Column(name = "policy_version", nullable = false)
    private String policyVersion;

    @Column(name = "policy_effective_at", nullable = false)
    private Instant policyEffectiveAt;

    @Column(name = "available_weight", nullable = false)
    private double availableWeight;

    @Column(name = "factors_available", nullable = false)
    private int factorsAvailable;

    @Column(name = "factors_total", nullable = false)
    private int factorsTotal;

    @Column(name = "is_reliable", nullable = false)
    private boolean reliable;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "factors", nullable = false, columnDefinition = "jsonb")
    private String factorsJson;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "evidence", nullable = false, columnDefinition = "jsonb")
    private String evidenceJson;

    @Column(name = "inputs_hash", nullable = false)
    private String inputsHash;

    @Column(name = "assessed_at", nullable = false)
    private Instant assessedAt;

    @Column(name = "observed_at", nullable = false)
    private Instant observedAt;

    @Column(name = "correlation_id", nullable = false)
    private String correlationId;

    protected AssessmentEntity() {
    }

    @SuppressWarnings("checkstyle:ParameterNumber")
    public AssessmentEntity(UUID assessmentId, UUID trackId, String sectorId, double compositeScore,
                            String level, String recommendation, String ruleId, String rationale,
                            String policyId, String policyVersion, Instant policyEffectiveAt,
                            double availableWeight, int factorsAvailable, int factorsTotal,
                            boolean reliable, String factorsJson, String evidenceJson,
                            String inputsHash, Instant assessedAt, Instant observedAt,
                            String correlationId) {
        this.assessmentId = assessmentId;
        this.trackId = trackId;
        this.sectorId = sectorId;
        this.compositeScore = compositeScore;
        this.level = level;
        this.recommendation = recommendation;
        this.ruleId = ruleId;
        this.rationale = rationale;
        this.policyId = policyId;
        this.policyVersion = policyVersion;
        this.policyEffectiveAt = policyEffectiveAt;
        this.availableWeight = availableWeight;
        this.factorsAvailable = factorsAvailable;
        this.factorsTotal = factorsTotal;
        this.reliable = reliable;
        this.factorsJson = factorsJson;
        this.evidenceJson = evidenceJson;
        this.inputsHash = inputsHash;
        this.assessedAt = assessedAt;
        this.observedAt = observedAt;
        this.correlationId = correlationId;
    }

    public UUID getAssessmentId() {
        return assessmentId;
    }

    public UUID getTrackId() {
        return trackId;
    }

    public String getSectorId() {
        return sectorId;
    }

    public double getCompositeScore() {
        return compositeScore;
    }

    public String getLevel() {
        return level;
    }

    public String getRecommendation() {
        return recommendation;
    }

    public String getRuleId() {
        return ruleId;
    }

    public String getRationale() {
        return rationale;
    }

    public String getPolicyId() {
        return policyId;
    }

    public String getPolicyVersion() {
        return policyVersion;
    }

    public Instant getPolicyEffectiveAt() {
        return policyEffectiveAt;
    }

    public double getAvailableWeight() {
        return availableWeight;
    }

    public int getFactorsAvailable() {
        return factorsAvailable;
    }

    public int getFactorsTotal() {
        return factorsTotal;
    }

    public boolean isReliable() {
        return reliable;
    }

    public String getFactorsJson() {
        return factorsJson;
    }

    public String getEvidenceJson() {
        return evidenceJson;
    }

    public String getInputsHash() {
        return inputsHash;
    }

    public Instant getAssessedAt() {
        return assessedAt;
    }

    public Instant getObservedAt() {
        return observedAt;
    }

    public String getCorrelationId() {
        return correlationId;
    }
}
