package pk.nust.cuas.threat.infrastructure.persistence;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import pk.nust.cuas.threat.domain.model.Recommendation;
import pk.nust.cuas.threat.domain.model.ThreatFactorType;
import pk.nust.cuas.threat.domain.model.ThreatLevel;
import pk.nust.cuas.threat.domain.policy.LevelThresholds;
import pk.nust.cuas.threat.domain.policy.NormalisationParameters;
import pk.nust.cuas.threat.domain.policy.RecommendationRule;
import pk.nust.cuas.threat.domain.policy.ReliabilityCondition;
import pk.nust.cuas.threat.domain.policy.ThreatPolicy;
import pk.nust.cuas.threat.domain.port.ThreatPolicyRepository;
import pk.nust.cuas.threat.infrastructure.persistence.entity.PolicyVersionEntity;
import pk.nust.cuas.threat.infrastructure.persistence.entity.RuleDefinitionEntity;
import pk.nust.cuas.threat.infrastructure.persistence.entity.ThreatPolicyEntity;
import pk.nust.cuas.threat.infrastructure.persistence.repository.PolicyVersionJpaRepository;
import pk.nust.cuas.threat.infrastructure.persistence.repository.ThreatPolicyJpaRepository;

/**
 * JPA adapter for {@link ThreatPolicyRepository}.
 *
 * <p>Publishing writes a new immutable version row and repoints the policy at it. There is no code
 * path here that rewrites an existing version, which is what keeps every assessment's cited
 * {@code policy_version} meaning the same thing tomorrow as it did when the score was written.
 */
@Repository
public class JpaThreatPolicyRepository implements ThreatPolicyRepository {

    private final ThreatPolicyJpaRepository policies;
    private final PolicyVersionJpaRepository versions;
    private final Clock clock;

    public JpaThreatPolicyRepository(ThreatPolicyJpaRepository policies,
                                     PolicyVersionJpaRepository versions,
                                     Clock clock) {
        this.policies = policies;
        this.versions = versions;
        this.clock = clock;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<ThreatPolicy> active() {
        return policies.findAll().stream()
                .findFirst()
                .flatMap(policy -> versions
                        .findByPolicyIdAndVersion(policy.getPolicyId(), policy.getActiveVersion()))
                .map(JpaThreatPolicyRepository::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<ThreatPolicy> byVersion(String version) {
        return versions.findByVersion(version).map(JpaThreatPolicyRepository::toDomain);
    }

    /**
     * {@inheritDoc}
     *
     * <p><strong>The parent row is written first.</strong> {@code policy_versions.policy_id} carries
     * a foreign key to {@code threat_policies}, so inserting the version before its policy exists
     * fails — which is precisely the path a fresh deployment takes when it seeds its section 6.3.4
     * defaults on first start. The ordering below is load-bearing, not incidental.
     */
    @Override
    @Transactional
    public ThreatPolicy publish(ThreatPolicy policy) {
        Instant now = Instant.now(clock);

        policies.findById(policy.policyId()).ifPresentOrElse(
                existing -> {
                    existing.activate(policy.version(), now);
                    policies.save(existing);
                },
                () -> policies.save(new ThreatPolicyEntity(
                        policy.policyId(), policy.policyId(),
                        "Composite threat scoring policy (ARCHITECTURE.md section 6.3.4)",
                        policy.version(), now)));
        policies.flush();

        PolicyVersionEntity version = toEntity(policy);
        policy.rules().forEach(rule -> version.addRule(toEntity(rule)));
        versions.saveAndFlush(version);

        return toDomain(version);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ThreatPolicy> versions() {
        return policies.findAll().stream()
                .findFirst()
                .map(policy -> versions.findByPolicyIdOrderByEffectiveAtDesc(policy.getPolicyId()))
                .orElseGet(List::of)
                .stream()
                .map(JpaThreatPolicyRepository::toDomain)
                .toList();
    }

    private static PolicyVersionEntity toEntity(ThreatPolicy policy) {
        NormalisationParameters curves = policy.normalisation();
        LevelThresholds thresholds = policy.thresholds();
        return new PolicyVersionEntity(
                policy.policyId(), policy.version(), policy.effectiveAt(),
                policy.weightOf(ThreatFactorType.MODEL_CONFIDENCE),
                policy.weightOf(ThreatFactorType.PROXIMITY),
                policy.weightOf(ThreatFactorType.CLOSING_VELOCITY),
                policy.weightOf(ThreatFactorType.TRAJECTORY_INTENT),
                policy.weightOf(ThreatFactorType.SENSOR_AGREEMENT),
                thresholds.medium(), thresholds.high(), thresholds.critical(),
                curves.proximityCriticalRangeM(), curves.proximityIgnoreRangeM(),
                curves.closureReferenceMs(), curves.intentConfirmationSweeps(),
                policy.reliabilityFloor());
    }

    private static RuleDefinitionEntity toEntity(RecommendationRule rule) {
        return new RuleDefinitionEntity(
                rule.ruleId(), rule.priority(), rule.minLevel().name(), rule.requireClosing(),
                rule.reliability().name(), rule.requireUnclassified(), rule.minConsecutiveClosing(),
                rule.recommendation().name(), rule.rationale());
    }

    private static ThreatPolicy toDomain(PolicyVersionEntity entity) {
        return new ThreatPolicy(
                entity.getPolicyId(),
                entity.getVersion(),
                entity.getEffectiveAt(),
                Map.of(
                        ThreatFactorType.MODEL_CONFIDENCE, entity.getWeightModelConfidence(),
                        ThreatFactorType.PROXIMITY, entity.getWeightProximity(),
                        ThreatFactorType.CLOSING_VELOCITY, entity.getWeightClosingVelocity(),
                        ThreatFactorType.TRAJECTORY_INTENT, entity.getWeightTrajectoryIntent(),
                        ThreatFactorType.SENSOR_AGREEMENT, entity.getWeightSensorAgreement()),
                new LevelThresholds(entity.getThresholdMedium(), entity.getThresholdHigh(),
                        entity.getThresholdCritical()),
                new NormalisationParameters(entity.getProximityCriticalRangeM(),
                        entity.getProximityIgnoreRangeM(), entity.getClosureReferenceMs(),
                        entity.getIntentConfirmationSweeps()),
                entity.getReliabilityFloor(),
                entity.getRules().stream().map(JpaThreatPolicyRepository::toDomain).toList());
    }

    private static RecommendationRule toDomain(RuleDefinitionEntity entity) {
        return new RecommendationRule(
                entity.getRuleId(), entity.getPriority(),
                ThreatLevel.valueOf(entity.getMinLevel()), entity.isRequireClosing(),
                ReliabilityCondition.valueOf(entity.getReliability()),
                entity.isRequireUnclassified(), entity.getMinConsecutiveClosing(),
                Recommendation.valueOf(entity.getRecommendation()), entity.getRationale());
    }
}
