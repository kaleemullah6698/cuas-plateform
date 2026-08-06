package pk.nust.cuas.threat.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/** JPA mapping for a {@code rule_definitions} row: one recommendation rule of one policy version. */
@Entity
@Table(name = "rule_definitions")
public class RuleDefinitionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "policy_version_id", nullable = false)
    private PolicyVersionEntity policyVersion;

    @Column(name = "rule_id", nullable = false)
    private String ruleId;

    @Column(name = "priority", nullable = false)
    private int priority;

    @Column(name = "min_level", nullable = false)
    private String minLevel;

    @Column(name = "require_closing", nullable = false)
    private boolean requireClosing;

    @Column(name = "reliability", nullable = false)
    private String reliability;

    @Column(name = "require_unclassified", nullable = false)
    private boolean requireUnclassified;

    @Column(name = "min_consecutive_closing", nullable = false)
    private int minConsecutiveClosing;

    @Column(name = "recommendation", nullable = false)
    private String recommendation;

    @Column(name = "rationale", nullable = false)
    private String rationale;

    protected RuleDefinitionEntity() {
    }

    @SuppressWarnings("checkstyle:ParameterNumber")
    public RuleDefinitionEntity(String ruleId, int priority, String minLevel, boolean requireClosing,
                                String reliability, boolean requireUnclassified,
                                int minConsecutiveClosing, String recommendation, String rationale) {
        this.ruleId = ruleId;
        this.priority = priority;
        this.minLevel = minLevel;
        this.requireClosing = requireClosing;
        this.reliability = reliability;
        this.requireUnclassified = requireUnclassified;
        this.minConsecutiveClosing = minConsecutiveClosing;
        this.recommendation = recommendation;
        this.rationale = rationale;
    }

    void attachTo(PolicyVersionEntity version) {
        this.policyVersion = version;
    }

    public String getRuleId() {
        return ruleId;
    }

    public int getPriority() {
        return priority;
    }

    public String getMinLevel() {
        return minLevel;
    }

    public boolean isRequireClosing() {
        return requireClosing;
    }

    public String getReliability() {
        return reliability;
    }

    public boolean isRequireUnclassified() {
        return requireUnclassified;
    }

    public int getMinConsecutiveClosing() {
        return minConsecutiveClosing;
    }

    public String getRecommendation() {
        return recommendation;
    }

    public String getRationale() {
        return rationale;
    }
}
