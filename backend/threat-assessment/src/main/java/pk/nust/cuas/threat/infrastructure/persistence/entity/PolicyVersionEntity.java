package pk.nust.cuas.threat.infrastructure.persistence.entity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * JPA mapping for a {@code policy_versions} row and its rules.
 *
 * <p>There are no setters. A published version is immutable — every assessment cites the version
 * that produced it, so an editable version would silently re-describe past scores — and the absence
 * of a mutator is the cheapest way to make that true of the code as well as of the intent.
 */
@Entity
@Table(name = "policy_versions")
public class PolicyVersionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "policy_id", nullable = false)
    private String policyId;

    @Column(name = "version", nullable = false)
    private String version;

    @Column(name = "effective_at", nullable = false)
    private Instant effectiveAt;

    @Column(name = "weight_model_confidence", nullable = false)
    private double weightModelConfidence;

    @Column(name = "weight_proximity", nullable = false)
    private double weightProximity;

    @Column(name = "weight_closing_velocity", nullable = false)
    private double weightClosingVelocity;

    @Column(name = "weight_trajectory_intent", nullable = false)
    private double weightTrajectoryIntent;

    @Column(name = "weight_sensor_agreement", nullable = false)
    private double weightSensorAgreement;

    @Column(name = "threshold_medium", nullable = false)
    private double thresholdMedium;

    @Column(name = "threshold_high", nullable = false)
    private double thresholdHigh;

    @Column(name = "threshold_critical", nullable = false)
    private double thresholdCritical;

    @Column(name = "proximity_critical_range_m", nullable = false)
    private double proximityCriticalRangeM;

    @Column(name = "proximity_ignore_range_m", nullable = false)
    private double proximityIgnoreRangeM;

    @Column(name = "closure_reference_ms", nullable = false)
    private double closureReferenceMs;

    @Column(name = "intent_confirmation_sweeps", nullable = false)
    private int intentConfirmationSweeps;

    @Column(name = "reliability_floor", nullable = false)
    private double reliabilityFloor;

    @OneToMany(mappedBy = "policyVersion", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("priority ASC")
    private List<RuleDefinitionEntity> rules = new ArrayList<>();

    protected PolicyVersionEntity() {
    }

    @SuppressWarnings("checkstyle:ParameterNumber")
    public PolicyVersionEntity(String policyId, String version, Instant effectiveAt,
                               double weightModelConfidence, double weightProximity,
                               double weightClosingVelocity, double weightTrajectoryIntent,
                               double weightSensorAgreement, double thresholdMedium,
                               double thresholdHigh, double thresholdCritical,
                               double proximityCriticalRangeM, double proximityIgnoreRangeM,
                               double closureReferenceMs, int intentConfirmationSweeps,
                               double reliabilityFloor) {
        this.policyId = policyId;
        this.version = version;
        this.effectiveAt = effectiveAt;
        this.weightModelConfidence = weightModelConfidence;
        this.weightProximity = weightProximity;
        this.weightClosingVelocity = weightClosingVelocity;
        this.weightTrajectoryIntent = weightTrajectoryIntent;
        this.weightSensorAgreement = weightSensorAgreement;
        this.thresholdMedium = thresholdMedium;
        this.thresholdHigh = thresholdHigh;
        this.thresholdCritical = thresholdCritical;
        this.proximityCriticalRangeM = proximityCriticalRangeM;
        this.proximityIgnoreRangeM = proximityIgnoreRangeM;
        this.closureReferenceMs = closureReferenceMs;
        this.intentConfirmationSweeps = intentConfirmationSweeps;
        this.reliabilityFloor = reliabilityFloor;
    }

    /** Attaches a rule, keeping both sides of the association consistent. */
    public void addRule(RuleDefinitionEntity rule) {
        rules.add(rule);
        rule.attachTo(this);
    }

    public Long getId() {
        return id;
    }

    public String getPolicyId() {
        return policyId;
    }

    public String getVersion() {
        return version;
    }

    public Instant getEffectiveAt() {
        return effectiveAt;
    }

    public double getWeightModelConfidence() {
        return weightModelConfidence;
    }

    public double getWeightProximity() {
        return weightProximity;
    }

    public double getWeightClosingVelocity() {
        return weightClosingVelocity;
    }

    public double getWeightTrajectoryIntent() {
        return weightTrajectoryIntent;
    }

    public double getWeightSensorAgreement() {
        return weightSensorAgreement;
    }

    public double getThresholdMedium() {
        return thresholdMedium;
    }

    public double getThresholdHigh() {
        return thresholdHigh;
    }

    public double getThresholdCritical() {
        return thresholdCritical;
    }

    public double getProximityCriticalRangeM() {
        return proximityCriticalRangeM;
    }

    public double getProximityIgnoreRangeM() {
        return proximityIgnoreRangeM;
    }

    public double getClosureReferenceMs() {
        return closureReferenceMs;
    }

    public int getIntentConfirmationSweeps() {
        return intentConfirmationSweeps;
    }

    public double getReliabilityFloor() {
        return reliabilityFloor;
    }

    public List<RuleDefinitionEntity> getRules() {
        return List.copyOf(rules);
    }
}
